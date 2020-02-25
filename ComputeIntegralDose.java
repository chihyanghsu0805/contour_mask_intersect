package sample.control;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;

import javax.swing.JFileChooser;

import com.mimvista.external.contouring.XMimContour;
import com.mimvista.external.control.XMimEntryPoint;
import com.mimvista.external.control.XMimLogger;
import com.mimvista.external.control.XMimSession;
import com.mimvista.external.data.XMimMutableNDArray;
import com.mimvista.external.data.XMimNDArray;
import com.mimvista.external.data.XMimRLEIterator;
import com.mimvista.external.linking.XMimLinkController;
import com.mimvista.external.points.XMimImageSpace;
import com.mimvista.external.points.XMimNoxelPointF;
import com.mimvista.external.points.XMimNoxelPointI;
import com.mimvista.external.series.XMimDose;
import com.mimvista.external.series.XMimImage;

public class ComputeIntegralDose {
	XMimSession session;
	XMimLogger logger;

	private static final String desc = "Compute Integral Dose For Each Contour.";

	/* 
	 * 	@XMimEntryPoint tells MIM that this function can be used to start a MIMextension
	 * 		Meta-data entered here will display in the extension launcher inside MIM
	 */

	@XMimEntryPoint(
			name="ComputeIntegralDose",
			author="Chih-Yang Hsu",
			category="Contours",
			description=desc
			)

	public Object[] run(XMimSession Sess, XMimImage Image) {

		logger = Sess.createLogger();
		String Filename = (String) Image.getInfo().getDicomInfo().getValueByName("PatientID");		
		Filename = Filename.substring(0, 5);
		Filename = Filename.concat(".IntegralDose.csv");

		//First, show a JFileChooser to let the user decide where to output the CSV file
		JFileChooser jfc = new JFileChooser();
		jfc.setDialogType(JFileChooser.SAVE_DIALOG);
		jfc.setSelectedFile(new File(Filename));
		jfc.showSaveDialog(null);
		File file = jfc.getSelectedFile();

		//create the object, and then start the actual writing process	
		Collection<XMimDose> AllDose = Sess.getAllDoseVolumes();

		WriteCsv(Sess, Image, file, AllDose);

		return new Object[0];
	}

	public void WriteCsv(final XMimSession Sess, final XMimImage Image, File csvFile, Collection<XMimDose> AllDose) {

		logger.debug("csv path "+csvFile.getAbsolutePath());

		BufferedWriter bo = null;

		try {

			bo = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile)));			

			Collection<XMimContour> AllContours = Image.getContours();

			float [] VoxelResolution = Image.getNoxelSizeInMm();

			String Data = "Tissue" + ",";

			for (XMimDose Dose : AllDose) {

				String Description = Dose.getDoseDescription();				
				Data = Data + Description.replaceAll(",", "_") + ",";

			}

			Data = Data + "\n";			

			for (XMimContour Contour : AllContours) {				

				String Name = Contour.getInfo().getName();
				logger.info(Name);				
				Data = Data + Name +",";				

				for (XMimDose Dose : AllDose) {

					double IntegralDose = 0;					
					IntegralDose = ComputeDose(Sess, Contour, Dose); 					
					IntegralDose = IntegralDose*VoxelResolution[0]*VoxelResolution[1]*VoxelResolution[2];					
					IntegralDose = IntegralDose*1e-3; // mm3 -> mL

					Data = Data + IntegralDose + ",";					
				}
				Data = Data + "\n";
			}			

			bo.write(Data);
			logger.debug("Done writing csv.");			
		}

		catch (Exception e) {
			logger.error("Unexpected IO exception", e);
		} 

		finally {			
			if (bo != null)
				try {
					bo.close();					
				} 
			catch (IOException e) {				
			}			
		}
	}	

	public double ComputeDose(XMimSession Session, XMimContour Contour, XMimDose Dose) {

		double IntegralDose = 0;

		XMimLinkController linker = Session.getLinker();
		XMimImage Image = Dose.getOwner();

		XMimImageSpace ImageSpace = Image.getSpace();
		XMimNoxelPointF ImageNoxelF = Image.createNoxelPointF();
		XMimNoxelPointI ImageNoxelI = Image.createNoxelPointI();
		XMimNDArray ImageData = Image.getScaledData();
		int[] ImageDims = ImageData.getDims();
		boolean [][][] ImageCheck = new boolean [ImageDims[0]][ImageDims[1]][ImageDims[2]];

		XMimImageSpace ContourSpace = Contour.getSpace();
		XMimNoxelPointF ContourNoxelF = Session.getPointFactory().createNoxelPoint(ContourSpace, new float[3]);
		XMimNoxelPointI ContourNoxelI = Session.getPointFactory().createNoxelPointI(ContourSpace, new int[]{0,0,0});
		XMimMutableNDArray ContourMask = Contour.getData();
		String Name = Contour.getInfo().getName();

		XMimNoxelPointF DoseNoxelF = Dose.createNoxelPointF();
		XMimNoxelPointI DoseNoxelI = Dose.createNoxelPointI();
		XMimNDArray DoseData = Dose.getScaledData();
		XMimImageSpace DoseSpace = Dose.getSpace();
		int[] DoseDims = DoseData.getDims();

		for (XMimRLEIterator lineIter : ContourMask.getRleIterable()) {

			int start = -1;
			while (lineIter.hasNext()) {

				lineIter.advanceToNextNewValue(ContourNoxelI);				
				int end = -1;
				if (lineIter.getBoolean()) {
					start = ContourNoxelI.getCoord(0);
					if (!lineIter.hasNext()) {
						end = lineIter.getCurrentLine().getDims()[0];
					}
				} else {
					end = ContourNoxelI.getCoord(0);
				}

				if (start != -1 && end != -1) {

					for(int i = start; i < end; i=i+1) {

						ContourNoxelI.setCoord(0, i);
						ContourNoxelF = ContourNoxelI.toVoxelCenter();
						ImageNoxelF = linker.toRawNoxel(ContourNoxelF, ImageSpace);
						ImageNoxelI = ImageNoxelF.toRawDataLocation();						

						if (!ImageCheck[ImageNoxelI.getCoord(0)][ImageNoxelI.getCoord(1)][ImageNoxelI.getCoord(2)]) {							
							ImageCheck[ImageNoxelI.getCoord(0)][ImageNoxelI.getCoord(1)][ImageNoxelI.getCoord(2)] = true;							
							
							double HU = 0;
							double DoseValue = 0;							
							
							if (ImageNoxelI.getCoord(0) >= ImageDims[0] || ImageNoxelI.getCoord(1) >= ImageDims[1] || ImageNoxelI.getCoord(2) >= ImageDims[2]) {								
								logger.info(Name + "ImageNoxel OutOfBounds");
							} else {								
								HU = ImageData.getDoubleValue(ImageNoxelI);
								HU = HU/1000+1;
							}						
							
							DoseNoxelF = linker.toRawNoxel(ContourNoxelF, DoseSpace);
							DoseNoxelI = DoseNoxelF.toRawDataLocation();
							
							if (DoseNoxelI.getCoord(0) >= DoseDims[0] || DoseNoxelI.getCoord(1) >= DoseDims[1] || DoseNoxelI.getCoord(2) >= DoseDims[2]) {								
								logger.info(Name + "DoseNoxel OutOfBounds");								
							} else {								
								DoseValue = DoseData.getDoubleValue(DoseNoxelI);								
							}
							IntegralDose = IntegralDose + DoseValue * HU;							
						}			
					}
					start = -1;
				}							
			}					
		}		
		return IntegralDose;
	}	
}