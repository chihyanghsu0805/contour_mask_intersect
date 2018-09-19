package sample.control;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.swing.JFileChooser;

import java.util.Collection;

import com.mimvista.external.control.XMimEntryPoint;
import com.mimvista.external.control.XMimLogger;
import com.mimvista.external.control.XMimSession;
import com.mimvista.external.data.XMimMutableNDArray;
import com.mimvista.external.data.XMimRLEIterator;
import com.mimvista.external.contouring.XMimContour;

import com.mimvista.external.points.XMimNoxelPointI;

import com.mimvista.external.series.XMimImage;

public class ContourIntersect {
	XMimSession session;

	public ContourIntersect(XMimSession session) {
		this.session = session;
	}

	private static final String desc = "Exports contours and computes interesected volume.";

	/* 
	 * 	@XMimEntryPoint tells MIM that this function can be used to start a MIMextension
	 * 		Meta-data entered here will display in the extension launcher inside MIM
	 */

	@XMimEntryPoint(

			name="ContourIntersect",
			author="Chih-Yang Hsu",
			category="Contours",
			description=desc

			)

	//public static Object[] run(XMimSession sess, XMimImage image, XMimDose dose) {
	public static Object[] run(XMimSession sess, XMimImage image) {

		String Filename = (String) image.getInfo().getDicomInfo().getValueByName("PatientID");
		
		Filename = Filename.substring(0, 5);
		String FilenameABS = Filename.concat(".AbsoluteML.csv");
		String FilenamePER = Filename.concat(".Percentage.csv");
		String FilenameBIN = Filename.concat(".Binary.csv");

		//First, show a JFileChooser to let the user decide where to output the CSV file
		JFileChooser jfc1 = new JFileChooser();
		jfc1.setDialogType(JFileChooser.SAVE_DIALOG);
		jfc1.setSelectedFile(new File(FilenameABS));
		jfc1.showSaveDialog(null);
		File fileABS = jfc1.getSelectedFile();
		
		JFileChooser jfc2 = new JFileChooser();
		jfc2.setDialogType(JFileChooser.SAVE_DIALOG);
		jfc2.setSelectedFile(new File(FilenamePER));
		jfc2.showSaveDialog(null);
		File filePER = jfc2.getSelectedFile();
		
		JFileChooser jfc3 = new JFileChooser();
		jfc3.setDialogType(JFileChooser.SAVE_DIALOG);
		jfc3.setSelectedFile(new File(FilenameBIN));
		jfc3.showSaveDialog(null);
		File fileBIN = jfc3.getSelectedFile();
		
		//create the object, and then start the actual writing process
		ContourIntersect csv = new ContourIntersect(sess);

		csv.writeCsv(image, fileABS, filePER, fileBIN);

		//since we have no outputs back to MIM, we return an empty array
		return new Object[0];
	}

	public static double ComputeIntersect(final XMimMutableNDArray Mask1, final XMimMutableNDArray Mask2, final XMimImage image) {

		double IntersectVolume = 0;

		XMimNoxelPointI ContourNoxelI = image.createNoxelPointI();

		for (XMimRLEIterator lineIter : Mask1.getRleIterable()) {

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

					for(int i=start; i<end; i++) {
						
						ContourNoxelI.setCoord(0, i);
						
						if(Mask2.getBooleanValue(ContourNoxelI)) {
							
							IntersectVolume = IntersectVolume+1;
						} 
					}
					start = -1;
				}
			}
		}
			
		return IntersectVolume;		
	}

	public void writeCsv(final XMimImage image, File csvFile1, File csvFile2, File csvFile3) {

		XMimLogger logger = session.createLogger();

		//XMimLogger can be used to output debugging information.
		//Extension logs go into the MIM log folder in a special directory
		logger.debug("csv path "+csvFile1.getAbsolutePath());

		BufferedWriter bo1 = null;
		BufferedWriter bo2 = null;
		BufferedWriter bo3 = null;
		
		try {
			//Create a writer for file output
			bo1 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile1)));
			bo2 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile2)));
			bo3 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile3)));

			String ID = (String) image.getInfo().getDicomInfo().getValueByName("PatientID");
			ID = ID.substring(0, 5);
			String DataAbsolute = ","+",";
			String DataPercent = ","+",";
			String DataBinary = ","+",";

			Collection<XMimContour> AllContours = image.getContours();
			int NumContours = AllContours.size();
			int[] Multiplier = null;

			// Write Header
			for (XMimContour Contour : AllContours) {

				String Name = Contour.getInfo().getName();

				DataAbsolute = DataAbsolute+Name+",";
				DataPercent = DataPercent+Name+",";
				DataBinary = DataBinary+Name+",";

				Multiplier = Contour.getMultiplier();
			}		

			DataAbsolute = DataAbsolute+"\n";
			DataPercent = DataPercent+"\n";
			DataBinary = DataBinary+"\n";

			// Write Volume
			float [] VoxelResolution = image.getNoxelSizeInMm();
			
			int Mask1Iterator = 0;
			int Mask2Iterator = 0;
			int IntersectVolumeBI = 0;
			
			double IntersectVolume = 0;			
			double IntersectVolumeMM3 = 0;
			double IntersectVolumeML = 0;
			double IntersectVolumePER = 0;
			
			double MultiplierX = Multiplier[0];
			double MultiplierY = Multiplier[1];
			double MultiplierZ = Multiplier[2];
			double[][] VolumeStorage= new double[NumContours][NumContours];

			for (XMimContour Contour1 : AllContours) {
				
				String Name1 = Contour1.getInfo().getName();						

				DataAbsolute = DataAbsolute+ID+","+Name1+",";
				DataPercent = DataPercent+ID+","+Name1+",";
				DataBinary = DataBinary+ID+","+Name1+",";

				XMimMutableNDArray Mask1 = Contour1.getData(); 		
				
				Mask2Iterator = 0;

				for (XMimContour Contour2 : AllContours) {					

					XMimMutableNDArray Mask2 = Contour2.getData();
					
					if (Mask1Iterator <= Mask2Iterator) {
						
						IntersectVolume = ComputeIntersect(Mask1, Mask2, image);
						
					} else {
						
						IntersectVolume = VolumeStorage[Mask2Iterator][Mask1Iterator];
					}
					
					VolumeStorage[Mask1Iterator][Mask2Iterator] = IntersectVolume;
					
					Mask2Iterator = Mask2Iterator+1;

					IntersectVolumeMM3 = IntersectVolume*VoxelResolution[0]*VoxelResolution[1]*VoxelResolution[2];
					IntersectVolumeMM3 = IntersectVolumeMM3 / MultiplierX / MultiplierY / MultiplierZ;					
					IntersectVolumeML = IntersectVolumeMM3 * 1e-3; // mm^3 to mL
					
					IntersectVolumeBI = 0;
					if (IntersectVolumeML >= 0.001) {
						IntersectVolumeBI = 1;						
					}

					DataAbsolute = DataAbsolute+IntersectVolumeML+",";
					DataBinary = DataBinary+IntersectVolumeBI+",";
				}
				
				for (int i=0; i<NumContours; i++) {
					
					IntersectVolumePER = VolumeStorage[Mask1Iterator][i] / VolumeStorage[Mask1Iterator][Mask1Iterator];
					DataPercent = DataPercent+IntersectVolumePER+",";
					
				}
				
				DataAbsolute = DataAbsolute+"\n";
				DataPercent = DataPercent+"\n";
				DataBinary = DataBinary+"\n";
				
				Mask1Iterator = Mask1Iterator+1;
			}			

			bo1.write(DataAbsolute);
			bo2.write(DataPercent);
			bo3.write(DataBinary);

			logger.debug("Done writing csv.");			
		}

		catch (Exception e) {
			logger.error("Unexpected IO exception", e);
		} 

		finally {			
			if (bo1 != null)
				try {
					bo1.close();
					bo2.close();
					bo3.close();
				} 
			catch (IOException e) {				
			}			
		}
	}
}