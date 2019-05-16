import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JFileChooser;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.plugin.frame.RoiManager;


/**
 * This ImageJ plugin calculates and prints relevant data from TIRF microscopy
 * image stacks and prints them as a csv.
 */
public class ActinEnrichment_ implements ExtendedPlugInFilter {

	ImagePlus imp;
	ImagePlus impThresh;
	ImagePlus impActin;
	ImagePlus impABP;
	Roi[] allRois;
	Roi[] rois;
	double[] totCountValABP;

	ArrayList<Integer> includedFilaments = new ArrayList<Integer>(); //holds the number of consecutive filament appearances for a given filament 
	ArrayList<Double> averageAreaForFilaments = new ArrayList<Double>(); //holds the average area of a filament that was tracked across multiple frames 
	ArrayList<Double> avgIntensityForFilaments = new ArrayList<Double>(); //holds the average ratiometric value for each filament that was tracked
	ArrayList<double[]> ratiosList = new ArrayList<double[]>(); //this will hold the individual values to calculate the standard deviation
	ArrayList<Double> sdForFilaments = new ArrayList<Double> ();//holds the Standard Deviation values of the avg intensity ratios 
	int numIncludedFilaments = 0; //total number of filaments that met all the requirements in a movie

	File actinFile = null;
	File ABPFile = null;
	String newFileLocation = null;

	Double thresholdTracking;
	Double particleSizeMin = null;
	Double percentBleach = null;
	Double filamentThreshold = null;
	Double maxFilamentArea = null;
	boolean doTracking = true;
	boolean doWatershed = false;
	boolean doRatiobg = true;


	//Arbitrary box size for background
	//Make sure this is an even number!
	Double boxDim;

	/**
	 * Method responsible for completing the image processing required. Will select all particles from each image in the image stack and 
	 * run the ratiometric analysis on those particles.
	 */
	public void run(ImageProcessor ip) {

		for(int i = 0; i < 10000; i++) {
			includedFilaments.add(0);
		}

		for(int i = 0; i < 10000; i++) {
			averageAreaForFilaments.add(0.0);
		}

		for(int i = 0; i < 10000; i++) {
			avgIntensityForFilaments.add(0.0);
		}

		for(int i = 0; i < 10000; i++) {
			sdForFilaments.add(0.0);
		}

		for(int i = 0; i < 10000; i++) {
			double[] e = new double[10];
			ratiosList.add(e);
		}
		//This is responsible for processing the images selected from ShowDialog() method
		if ( particleSizeMin != null && actinFile != null && ABPFile != null && newFileLocation != null) {
			ParticleSelector(actinFile);     //Will use Analyze Particles to select rois that represent actin filaments   
			try {
				determineEnrichment(actinFile, ABPFile, rois); //Runs the ratiometric image analysis between the actin & ABP image stacks
				IJ.showMessage("Colocalization Done!");
			} catch (IOException e) {
				//Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	/**
	 * This specifies the parameters that will be used
	 */
	private void determineParameters() {
		GenericDialog dialogBox = new GenericDialog("Parameters");

		String[] items = { "YES", "NO" }; 
		dialogBox.addChoice("Do tracking?", items, items[0]); // enable tracking of particles
		dialogBox.addChoice("Do Watershed?", items, items[1]); //for highly dense filaments 
		dialogBox.addChoice("Ratio of ABP background?", items, items[1]);
		dialogBox.addSlider("Size of Background box (Must be even number)", 0, 80, 60);
		dialogBox.addSlider("Minimum distance for tracking", 0, 50, 25);
		dialogBox.addSlider("Min number of consecutive appearences", 1, 50, 10);
		dialogBox.addSlider("Maximum Filament Area:", 50, 1500, 1000); 
		dialogBox.addSlider("Minimum Particle Size", 50, 1500, 100);

		dialogBox.showDialog();

		if (dialogBox.wasCanceled()) {
			return;
		}

		String tracking = dialogBox.getNextChoice();
		if (tracking.equals("YES")) {
			doTracking = true;
		} else if (tracking.equals("NO")) {
			doTracking = false;
		}

		String watershed = dialogBox.getNextChoice();
		if (watershed.equals("YES")) {
			doWatershed = true;
		} else if (tracking.equals("NO")) {
			doWatershed = false;
		}

		String ratioBackground = dialogBox.getNextChoice();
		if(ratioBackground.equals("YES")) {
			doRatiobg = true;
		} else if (ratioBackground.equals("NO")) {
			doRatiobg = false;
		}

		boxDim = dialogBox.getNextNumber();
		thresholdTracking = dialogBox.getNextNumber();
		filamentThreshold = dialogBox.getNextNumber();
		maxFilamentArea = dialogBox.getNextNumber();
		particleSizeMin = dialogBox.getNextNumber();

	}

	@Override
	/**
	 * This method requests user input. It also requires the user to specify
	 * where the files are located and where the intermediate/results ones will be
	 * stored.
	 */
	public int showDialog(ImagePlus imp, String command,
			PlugInFilterRunner pfr) {

		JFileChooser chooser = new JFileChooser();

		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		chooser.setDialogTitle("Please select the original actin stack");
		int returnVal = chooser.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			actinFile = chooser.getSelectedFile();
		} else if(returnVal == JFileChooser.CANCEL_OPTION) {
			return DONE;
		}

		chooser.setDialogTitle("Please select the original ABP stack");
		returnVal = chooser.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			ABPFile = chooser.getSelectedFile();
		} else if (returnVal == JFileChooser.CANCEL_OPTION) {
			return DONE;
		}

		chooser.setDialogTitle(
				"Please select the folder in which the intermediate files will be stored");
		returnVal = chooser.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			newFileLocation = chooser.getSelectedFile().getAbsolutePath() + "/";
			System.out.println("This is the path of new file: " + newFileLocation);
		} else if (returnVal == JFileChooser.CANCEL_OPTION) {
			return DONE;
		}

		determineParameters();

		return 0;
	}

	/**
	 * This method performs the particle selection operations on the image
	 * passed to it.
	 * 
	 * @param file This is the actin file that was selected from the dialog box
	 */
	private void ParticleSelector(File file) {
		// open the image
		imp = IJ.openImage(file.getAbsolutePath());

		//preprocessing making the binary mask for analyze particles
		IJ.run(imp, "Unsharp Mask...", "radius=1 mask=0.60 stack"); 
		System.out.println("Made it past unsharp mask");
		IJ.run(imp, "Median...", "radius=2 stack");
		System.out.println("Made it past median filter");
		IJ.run(imp, "Subtract Background...", "rolling=50 stack");
		System.out.println("Made it past bg_subtract");
		IJ.run(imp, "Make Binary", "method=Moments background=Dark calculate");
		System.out.println("Made binary mask");

		if(doWatershed) {
			IJ.run(imp, "Watershed", "stack");
			System.out.println("Finished Watershed");
		}

		IJ.run(imp, "Analyze Particles...", "size=" + particleSizeMin + "-Infinity show=[Count Masks] exclude clear add stack");

		//Get the thresholded image from Analyze Particles for the correct background subtraction 
		impThresh = WindowManager.getImage("Count Masks of " + file.getName());

		//Get dimensions of the entire image to determine which regions to include
		int imgHeight = imp.getImageStack().getProcessor(1).getHeight();
		int imgWidth = imp.getImageStack().getProcessor(1).getWidth();



		//Store the regions of interest in the rois array and save the ROIs for future references
		//Need to pre-process regions where their center is out of image parameters to prevent getting
		//inaccurate background intensity values 
		RoiManager manager = RoiManager.getInstance();
		allRois = new Roi[manager.getCount()];
		int roisCount = 0;

		//iterates through all regions created by the Analyze Particles function
		for (int i = 0; i < manager.getCount(); i++) {
			Roi currentRoi = manager.getRoi(i);
			double[] centroidCoor = currentRoi.getContourCentroid();
			Centroid centroid = new Centroid(centroidCoor[0], centroidCoor[1]);

			//series of if statements to check if a particular region will cause a background box that
			//does not fit the parameters of the image
			if((centroid.x - (boxDim/2)) < 0 || (centroid.y - (boxDim/2)) < 0) {
				allRois[i] = null;
			} else if((centroid.x + (boxDim/2)) > imgWidth || (centroid.y + (boxDim/2)) > imgHeight) {
				allRois[i] = null;
			} else {
				allRois[i] = manager.getRoi(i);
				roisCount++;
			}
		}

		//final rois and their centroids are placed in a new array 
		rois = new Roi[roisCount];

		int m = 0;
		for(int i = 0; i < allRois.length; i++) {
			if(allRois[i] != null) {
				//inserts roi within image bounds
				rois[m] = allRois[i];
				m++;
			}
		}

		manager.close();
		imp.close();
	}

	/**
	 * Once the stacks have been processed, data is collected from the ROIs using this method to compute the final ratiometric values. 
	 * The method also eliminates any values that do not meet the required number of consecutive filaments or average area across frames
	 * determined by the GUI inputs.
	 * @param fileActin The Actin channel movie selected from the GUI
	 * @param fileABP The ABP channel movie selected from the GUI
	 * @param rois The regions of interest (rois) extracted using the Analyze Particles function in the ParticleSelector method
	 * @throws IOException 
	 */
	private void determineEnrichment(File fileActin, File fileABP, Roi[] rois) throws IOException {

		//opens the stacks
		impActin = IJ.openImage(fileActin.getAbsolutePath());
		impABP = IJ.openImage(fileABP.getAbsolutePath());
		int slices = impABP.getStackSize();

		// Hash ROIs into frames and sort them accordingly by tracking filaments across frames
		ArrayList<Roi>[] roisInEachFrame = hashROIs(rois);  

		//Intensities[Slice][ROI Intensity Info] 
		Intensities[][] IntensityData = new Intensities[roisInEachFrame.length][];
		int frameCount = 0;


		//keeps track of total intensity per image in stack (of ABP channel) to check for photobleaching
		totCountValABP = new double[slices+1];

		//compute the necessary values for each filament in each frame in the image stack
		for(ArrayList<Roi> frame : roisInEachFrame){
			IntensityData[frameCount] = determineIntensities(frame, frameCount, impActin, impABP, impThresh);
			frameCount++;
		}


		//***************CREATE FOR LOOP TO ITERATE THROUGH IntensityData 2D ARRAY TO GET NECCESSARY INFO****************//

		if(doTracking) {
			File output = new File(newFileLocation + "\\" + actinFile.getName() + "_TrackingDataOutput.csv");
			PrintWriter pw2 = null;
			try {
				pw2 = new PrintWriter(output);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

			StringBuilder builder2 = new StringBuilder();
			String ColumnNamesList2 = "FrameNo,Filament,Area,NormalizedAvgActin,NormalizedAvgABP,ABP/Act" + "," + "," + "," + "FrameNo,TotABPInten" + 
					"," + "," +"Number of Filaments";
			builder2.append(ColumnNamesList2 + "\n");

			int prevSlice = -1;
			int prevFilament = -1;

			boolean added = false;
			boolean filamentAdded = false;
			boolean firstLine = true;

			int g = 0;
			int numForAreaAvg = 0;
			int numForFilamentAvg = 0;
			int numOfFilaments = 0; //keeps track of how many filaments meet the required consecutive number of appearences 


			ArrayList<Position> finalRois = new ArrayList<Position>();

			//calculate the average area for filaments that appear consecutively the right amount of time
			//also will add the position of the Rois that meet both requirements for consecutive appearances and average area 
			while(g < includedFilaments.size()) { //loops through the filaments (rows index for intensities 2D array)
				if(includedFilaments.get(g) >= filamentThreshold) {	
					numOfFilaments++;

					//Necessary for calculating SD
					int size = includedFilaments.get(g);
					double[] ratios = new double[size + 1];
					int ratiosCounter = 0;

					for(int i = 1; i < IntensityData.length; i++) { //loops through all frames in a movie (column index)
						if(IntensityData[i] != null ) {
							if(g < IntensityData[i].length) {
								if(IntensityData[i][g] != null) {
									double currentValAvg = averageAreaForFilaments.get(g);
									double valToBeAddedAvg = IntensityData[i][g].areaInsideROIs;
									averageAreaForFilaments.set(g, (currentValAvg + valToBeAddedAvg));
									numForAreaAvg++;

									//calculates the average intensity for a filament across multiple frames
									double currentValInt = avgIntensityForFilaments.get(g);
									double valToBeAddedInt = IntensityData[i][g].ratio;

									//add this value to the current list of ratios for this particular filament that has been tracked
									ratios[ratiosCounter] = valToBeAddedInt;
									ratiosCounter++;

									avgIntensityForFilaments.set(g,(currentValInt + valToBeAddedInt));
									numForFilamentAvg++;

									finalRois.add(new Position(i,g));
								}
							} 
						}
					}

					double avgIntForFilament = (avgIntensityForFilaments.get(g)/numForFilamentAvg);

					averageAreaForFilaments.set(g, (averageAreaForFilaments.get(g)/numForAreaAvg));
					avgIntensityForFilaments.set(g, avgIntForFilament);
					ratiosList.set(g, ratios);

					//calculate SD for a particular set of included filaments
					sdForFilaments.set(g, StandardDev(avgIntForFilament, ratiosList.get(g)));

					numForAreaAvg = 0;
					numForFilamentAvg = 0;
					numIncludedFilaments++;
					g++;
				} else {
					g++;
				}
			}

			System.out.println("The number of filaments after consecutive appearences: " + numOfFilaments);

			//Writes to a csv file the intensity values of Rois that meet both area and consecutive filament requirements 
			for(int m = 0; m < IntensityData.length; m++) {
				if(IntensityData[m] != null) {
					int size = IntensityData[m].length;

					for(int l = 0; l < size; l++) {
						if(includedFilaments.get(l) >= filamentThreshold && averageAreaForFilaments.get(l) < maxFilamentArea) {

							if(prevSlice == m) {
								added = true;
							} else {
								added = false;
							}

							if(l <= prevFilament) {
								filamentAdded = true;
							} else {
								filamentAdded = false;
								prevFilament = l;
							}

							//If a null value is reached it will be replaced by zeros 
							if(IntensityData[m][l] == null) {
								if(!filamentAdded) {
									if(!added) {
										if(firstLine) {
											builder2.append((m) + "," + (l) + "," + 0 + "," + 0  + "," + 0 + "," + 0 + "," + avgIntensityForFilaments.get(l) + "," 
													+ sdForFilaments.get(l) + "," + m + "," + totCountValABP[m] + "," + "," + numIncludedFilaments);

											builder2.append('\n');
											prevSlice = m;

											firstLine = false;
										} else {
											builder2.append((m) + "," + (l) + "," + 0 + "," + 0  + "," + 0 + "," + 0 + "," + avgIntensityForFilaments.get(l) + 
													"," + sdForFilaments.get(l) + "," + m + "," + totCountValABP[m]);

											builder2.append('\n');
											prevSlice = m;

										}
									} else {
										builder2.append((m) + "," + (l) + "," + 0 + "," + 0  + "," + 0 + "," + 0 + "," + avgIntensityForFilaments.get(l) + ","
												+ sdForFilaments.get(l));

										builder2.append('\n');

									}
								} else {
									if(!added) {
										builder2.append((m) + "," + (l) + "," + 0 + "," + 0  + "," + 0 + "," + 0 + "," + "," + "," + m + "," + 
												totCountValABP[m]);

										builder2.append('\n');
										prevSlice = m;	
									} else {
										builder2.append((m) + "," + (l) + "," + 0 + "," + 0  + "," + 0 + "," + 0 + ",");

										builder2.append('\n');
									}
								}
							} else {
								if(!filamentAdded) {
									if(!added) {
										if(firstLine) {
											Intensities dataPoint = IntensityData[m][l];
											builder2.append((m) + "," + (l) + "," + dataPoint.areaInsideROIs + "," + dataPoint.normAvgAct  
													+ "," + dataPoint.normAvgABP + "," + dataPoint.ratio + "," + avgIntensityForFilaments.get(l) + 
													"," + sdForFilaments.get(l) + "," + m + "," + totCountValABP[m] + "," + "," + numIncludedFilaments);

											builder2.append('\n');
											prevSlice = m;

											firstLine = false;
										} else {
											Intensities dataPoint = IntensityData[m][l];
											builder2.append((m) + "," + (l) + "," + dataPoint.areaInsideROIs + "," + dataPoint.normAvgAct  
													+ "," + dataPoint.normAvgABP + "," + dataPoint.ratio + "," + avgIntensityForFilaments.get(l) 
													+ "," + sdForFilaments.get(l) + "," + m + "," + totCountValABP[m]);

											builder2.append('\n');
											prevSlice = m; 

										}
									} else {
										Intensities dataPoint = IntensityData[m][l];
										builder2.append((m) + "," + (l) + "," + dataPoint.areaInsideROIs + "," + dataPoint.normAvgAct  
												+ "," + dataPoint.normAvgABP + "," + dataPoint.ratio + "," + avgIntensityForFilaments.get(l) + ","
												+ sdForFilaments.get(l));

										builder2.append('\n');

									} 
								} else {
									if(!added) {
										Intensities dataPoint = IntensityData[m][l];
										builder2.append((m) + "," + (l) + "," + dataPoint.areaInsideROIs + "," + dataPoint.normAvgAct  
												+ "," + dataPoint.normAvgABP + "," + dataPoint.ratio + "," + "," + "," + m + "," + totCountValABP[m]);

										builder2.append('\n');
										prevSlice = m; 

									} else {
										Intensities dataPoint = IntensityData[m][l];
										builder2.append((m) + "," + (l) + "," + dataPoint.areaInsideROIs + "," + dataPoint.normAvgAct  
												+ "," + dataPoint.normAvgABP + "," + dataPoint.ratio + ",");

										builder2.append('\n');
									}
								}
							}
						}
					}
				} 
			}

			impActin.close();
			impABP.close();
			pw2.write(builder2.toString());
			pw2.close();

			Sort(output);

			//Will show and save the Rois that meet the requirements listed above (Area & Consecutive Filaments) 
			RoiManager manager = new RoiManager();

			int h = 0;
			while(h < finalRois.size()) {
				outerloop:
					for(int c = 1; c < roisInEachFrame.length; c++) {
						for(int r = 0; r < roisInEachFrame[c].size(); r++) {
							if(c == finalRois.get(h).getPositionC() && r == finalRois.get(h).getPositionR()) {
								if(averageAreaForFilaments.get(r) < maxFilamentArea) {
									manager.addRoi(roisInEachFrame[c].get(r));
									h++;
									break outerloop;
								} else {
									h++;
									break outerloop;
								}
							} 
						}
					}
			}

			manager.runCommand("Save", newFileLocation + "\\" + actinFile.getName() + "_RoiSet.zip");

		} else {

			// prepares the printer to create the csv file
			PrintWriter pw = null;
			try {
				pw = new PrintWriter(new File(newFileLocation + "\\" + actinFile.getName() + "_DataOutput.csv"));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

			StringBuilder builder = new StringBuilder();
			String ColumnNamesList = "FrameNo,Area,NormalizedAvgActin,NormalizedAvgABP,ABP/Act";
			builder.append(ColumnNamesList + "\n");

			for(int m = 0; m < IntensityData.length; m++) {
				if(IntensityData[m] != null) {
					int size = IntensityData[m].length;

					for(int l = 0; l < size; l++) {
						double normAct = (IntensityData[m][l].averageAct - IntensityData[m][l].averageActBack);
						double normAvgABP = (IntensityData[m][l].averageABP - IntensityData[m][l].averageABPBack);
						builder.append((m) + "," + IntensityData[m][l].areaInsideROIs + "," + normAct  + "," + normAvgABP + "," + normAvgABP/normAct);

						builder.append('\n');
					}
				} 
			}

			impActin.close();
			impABP.close();
			pw.write(builder.toString());
			pw.close();
		}    
	}

	/**
	 * This method generates an array of Intensities objects that correspond to all the tracked rois in a particular frame of the movie
	 * @param frame An ArrayList of all the Rois from a particular frame in their correct order (tracked)  
	 * @param frameCount The integer that represents the frame of the movie that is being considered 
	 * @param impAct The actin channel image that corresponds to this frame in the movie
	 * @param impABP The ABP channel image that corresponds to this frame in the movie
	 * @param impThresh The Count Mask image produced by Analyze Particles that represents all the rois in this particular frame (thresholded) 
	 * @return An array of Intensities objects that hold the necessary values to compute the desired normalized ratios for this particular frame
	 */
	private Intensities[] determineIntensities (ArrayList<Roi> frame, int frameCount, 
			ImagePlus impAct, ImagePlus impABP, ImagePlus impThresh) {
		if(frame.size() == 0 || frameCount == 0){

			return null;
		} else {

			//need to get the imageProcessor for the slice we are currently at
			ImageProcessor tempIpActin = impAct.getImageStack().getProcessor(frameCount); 
			ImageProcessor tempIpABP = impABP.getImageStack().getProcessor(frameCount);
			ImageProcessor tempIpThresh = impThresh.getImageStack().getProcessor(frameCount);

			Intensities[] dataForSlice = new Intensities[frame.size()];

			//System.out.println("This is the value outside the image: " + tempIpActin.get(largeFrameWidth+1, largeFrameHeight+1));

			//iterate through all ROIs in this particular slice
			for(int i = 0; i < frame.size(); i++) {

				//variables to calculate for each filament
				double actinBackgroundIntensity = 0;
				double ABPBackgroundIntensity = 0;
				double actinIntensity = 0;
				double ABPIntensity = 0;

				int totPixelsOutside = 0;

				//checks if this particular Roi exists in this frame from the image stack
				if(frame.get(i) != null) {
					//find centroid for a given ROI in this slice of the stack
					Roi currentROI = frame.get(i);
					double[] centroidCoor = currentROI.getContourCentroid();
					Centroid centroid = new Centroid(centroidCoor[0], centroidCoor[1]);

					//create background box (represented as an ROI).
					//this is done to find the point coordinates of our background box with respect to the 
					//slice in the stack.
					Roi backgroundRoi = new Roi( ((int) centroid.getX()-(boxDim/2)), ((int) centroid.getY()-(boxDim/2)), boxDim, boxDim);
					Point[] pointsInBackgroundBox = backgroundRoi.getContainedPoints();

					//this will generate the sum of all intensities that are in the background box but not in any of the rois generated (all filaments)
					for (int v = 0; v < pointsInBackgroundBox.length; v++) {
						int xVal = pointsInBackgroundBox[v].x;
						int yVal = pointsInBackgroundBox[v].y;

						if(tempIpThresh.get(xVal, yVal) == 0) {
							actinBackgroundIntensity = actinBackgroundIntensity + tempIpActin.get(xVal, yVal);
							ABPBackgroundIntensity = ABPBackgroundIntensity + tempIpABP.get(xVal, yVal);
							totPixelsOutside++;
						} 
					}


					Point[] pointsInCurrentRoi = currentROI.getContainedPoints();
					int totPixelsInside = pointsInCurrentRoi.length; //get the area of the current roi

					//calculate intensities inside ROIs for both channels
					for(Point p : pointsInCurrentRoi) {
						actinIntensity = actinIntensity + tempIpActin.get(p.x, p.y);
						ABPIntensity = ABPIntensity + tempIpABP.get(p.x, p.y);
					}

					//generate the intensity object which will hold the important intensity values that will later be used to 
					//generate the corrected values for quantification
					Intensities currentIntenData = new Intensities(actinBackgroundIntensity, ABPBackgroundIntensity, actinIntensity, 
							ABPIntensity, totPixelsOutside, totPixelsInside);
					dataForSlice[i] = currentIntenData;

					//This calculates the mean intensity value for the entire background box to later use for analysis of photobleaching effects
					totCountValABP[frameCount] = totCountValABP[frameCount] + (ABPIntensity+ABPBackgroundIntensity)/(totPixelsOutside+totPixelsInside);
				} else {
					dataForSlice[i] = null;
				}
			}

			return dataForSlice;
		}
	}

	/**
	 * This method sorts the rois generated by analyze particles into their respective frames and then calls on other methods to complete 
	 * the tracking and organization of all the rois across multiple frames in a movie
	 * @param clearedRois An array of all the rois that meet the initial requirements outlined by the method ParticleSelector 
	 * @return An array of ArrayLists of rois which are sorted correctly (tracked) 
	 */
	private ArrayList<Roi>[] hashROIs(Roi[] clearedRois) {

		if(clearedRois.length == 0) {
			System.out.println("There are no ROIs that meet criteria!");
			return null;
		}

		//This gives the number of frames in this particular image stack
		int size = clearedRois[(clearedRois.length-1)].getPosition()+1;

		@SuppressWarnings("unchecked")
		ArrayList<Roi>[] roisInEachFrame = new ArrayList[size];

		for(int i = 0; i < roisInEachFrame.length; i++){   //populate ArrayList with new ArrayLists that will hold 
			//all the ROIs of a given slice in the stack 
			roisInEachFrame[i] = new ArrayList<Roi>();
		}

		for (Roi roi : clearedRois) {
			int frame = roi.getPosition();
			roisInEachFrame[frame].add(roi);	 
		}

		if(!doTracking) {
			return roisInEachFrame;
		} else {

			for(int currImg = 1; currImg < size; currImg++) {
				if((currImg - 1) > 0) {

					ArrayList<Roi> currentRois = copy(roisInEachFrame[currImg]);
					ArrayList<Roi> prevRois = copy(roisInEachFrame[(currImg-1)]);
					int smallerRoiListSize = currentRois.size();
					int largerRoiListSize = prevRois.size();

					//Scenario where a certain image in the stack contains zero ROIs (filaments)
					if(smallerRoiListSize > 0) {

						//Checks if the sorted list is larger than the unsorted list
						if(roisInEachFrame[(currImg - 1)].size() > roisInEachFrame[currImg].size()) {
							for(int i = smallerRoiListSize; i < largerRoiListSize; i++) {
								roisInEachFrame[currImg].add(null); //makes the unsorted list at least as large as the sorted list
							}
						}

						//calculate the distances between each of the Rois in both images 
						double[] minDistanceArray = minDistanceArray(prevRois, currentRois);

						//sort the distance arrays in ascending fashion
						Arrays.sort(minDistanceArray);

						ArrayList<Roi> sortedRois = new ArrayList<>(); //Will hold the values of the ROIs that have already been sorted 

						//track the rois across the different 
						trackRois(sortedRois, roisInEachFrame, minDistanceArray, prevRois, currentRois, currImg);

						//insert new rois that were not tracked
						insertNewRois(sortedRois, roisInEachFrame, prevRois, currentRois, currImg);
					}

				}

			}

			return roisInEachFrame;
		} 
	}

	/**
	 * This method calculates the distance between each roi in both the sorted list of rois and the list of rois to be sorted,
	 * in order to track rois based on how far apart their centroids are 
	 * @param prevRois The sorted list of rois
	 * @param currentRois The list of rois to be sorted
	 * @return An array that contains the euclidean distance between all possible pair of rois from the two lists passed as parameters
	 */
	private double[] minDistanceArray (ArrayList<Roi> prevRois, ArrayList<Roi> currentRois) {

		int minArrayCount = 0;
		double minDistanceArray[] = new double[prevRois.size()*currentRois.size()];

		for(int i = 0; i < prevRois.size(); i++) {
			for(int j = 0; j < currentRois.size(); j++) {

				//compare centroids (euclidean distance)
				if(prevRois.get(i) != null && currentRois.get(j) != null) {
					double[] firstCentroidCoor = prevRois.get(i).getContourCentroid();
					Centroid firstCentroid = new Centroid(firstCentroidCoor[0], firstCentroidCoor[1]);

					double[] secondCentroidCoor = currentRois.get(j).getContourCentroid();
					Centroid secondCentroid = new Centroid(secondCentroidCoor[0], secondCentroidCoor[1]);

					double tempDistance = distance(firstCentroid, secondCentroid);

					minDistanceArray[minArrayCount] = tempDistance;
					minArrayCount++;
				} else {
					minDistanceArray[minArrayCount] = thresholdTracking + (1+minArrayCount); //forced to not be considered
					minArrayCount++;
				}
			}	
		}

		return minDistanceArray;
	}

	/**
	 * This method rearranges the array of ArrayLists of rois by pairing the two rois from each of the two lists of rois
	 * that generate the shortest distance. This ultimately sorts the unsorted list so that rois that are close to one another 
	 * share the same position on their respective ArrayList. 
	 * @param sortedRois An ArrayList of rois that includes all the rois that were sorted/paired with an roi from the previous frame
	 * @param roisInEachFrame The array of ArrayLists of rois that is being organized
	 * @param minDistanceArray An array that contains the euclidean distance between all possible pair of rois from the two lists passed as parameters
	 * @param prevRois A copy of the ArrayList of rois from the previous frame which has already been sorted
	 * @param currentRois A copy of the ArrayList of rois from the current frame which has not been sorted 
	 * @param currImg The integer value that represents the current frame in the movie
	 */
	private void trackRois (ArrayList<Roi> sortedRois, ArrayList<Roi>[] roisInEachFrame, double[] minDistanceArray, ArrayList<Roi> prevRois,
			ArrayList<Roi> currentRois, int currImg) {

		int minArrayCount = 0; //holds the position index of the array that stores all the distances between ROIs in the two consecutive lists

		while(minDistanceArray[minArrayCount] < thresholdTracking && minArrayCount < minDistanceArray.length) {
			outerloop:
				for(int a = 0; a < prevRois.size(); a++) {
					for(int b = 0; b < currentRois.size(); b++) {

						Roi firstRoi = prevRois.get(a);
						Roi secondRoi = currentRois.get(b);

						Centroid firstCentroid;
						Centroid secondCentroid;

						double tempDistance;

						if(firstRoi != null) {
							double[] firstCentroidCoor = firstRoi.getContourCentroid();
							firstCentroid = new Centroid(firstCentroidCoor[0], firstCentroidCoor[1]);

							double[] secondCentroidCoor = secondRoi.getContourCentroid();
							secondCentroid = new Centroid(secondCentroidCoor[0], secondCentroidCoor[1]);

							tempDistance = distance(firstCentroid, secondCentroid);
						} else {
							break;
						}

						if (minDistanceArray[minArrayCount] == tempDistance) {

							boolean included = false; //checks if the secondRoi (in the currImg) has already been paired
							boolean included2 = false; //checks if a previous Roi (which pairs better ie smaller distance) has already been paired 

							//checks to see if the secondRoi has previously been paired AND prevents the secondRoi from pairing with a 
							//better matched pair of Rois 
							if(sortedRois.size() > 0) {
								for(int z = 0; z < sortedRois.size(); z++) {
									if(secondRoi.equals(sortedRois.get(z))) {
										included = true;
									} 
									if(roisInEachFrame[currImg].get(a) == null) {
										included2 = false;
									} else if(roisInEachFrame[currImg].get(a).equals(sortedRois.get(z))){
										included2 = true;
									}
								}
							}

							if(!included) {
								if(a == b) {
									minArrayCount++;
									sortedRois.add(secondRoi);

									//adjusts the size of the arraylist for this particular filament if necessary
									if(a < includedFilaments.size()) {
										includedFilaments.set(a, (includedFilaments.get(a) + 1));
									} else {
										includedFilaments.add(a, 1);
									}

									break outerloop;
								} else if(!included2) {
									roisInEachFrame[currImg].set(a, secondRoi);

									roisInEachFrame[currImg].set(b, null);

									minArrayCount++;
									sortedRois.add(secondRoi);

									if(a < includedFilaments.size()) {
										includedFilaments.set(a, (includedFilaments.get(a) + 1));
									} else {
										includedFilaments.add(1);
									}

									break outerloop;
								} else {	
									minArrayCount++;
									break outerloop;
								}
							} else {
								minArrayCount++;
								break outerloop;
							}
						} 

					}
				}
		}

	}

	/**
	 * This method is called by the hashROIs method to insert any rois that were not matched with an roi from the previous frame 
	 * @param sortedRois An ArrayList of rois that includes all the rois that were sorted/paired with an roi from the previous frame
	 * @param roisInEachFrame The array of ArrayLists of rois that is being organized 
	 * @param prevRois A copy of the ArrayList of rois from the previous frame which has already been sorted
	 * @param currentRois A copy of the ArrayList of rois from the current frame which has not been sorted 
	 * @param currImg The integer value that represents the current frame in the movie
	 */
	private void insertNewRois (ArrayList<Roi> sortedRois, ArrayList<Roi>[] roisInEachFrame, ArrayList<Roi> prevRois, ArrayList<Roi> currentRois, int currImg) {
		for(int a = 0; a < roisInEachFrame[currImg].size(); a++) {
			
			//Inserts rois that were not paired during the tracking
			if(roisInEachFrame[currImg].get(a) != null) {
				Roi tempRoi = roisInEachFrame[currImg].get(a);

				boolean included = false;

				for(int b = 0; b < sortedRois.size(); b++) {
					if(sortedRois.get(b) == null) {
						break;
					} else {
						if(tempRoi.equals(sortedRois.get(b))) {
							included = true;
							break;
						} 
					}
				}

				if(!included) {
					roisInEachFrame[currImg].add(tempRoi);
					roisInEachFrame[currImg].set(a, null);
					sortedRois.add(tempRoi);
				}
			}

		}

		//Inserts rois that were replaced by other rois during the tracking  
		for(int b = 0; b < currentRois.size(); b++) {
			if(currentRois.get(b) != null) {

				Roi tempRoi = currentRois.get(b);
				boolean included = false;

				for(int c = 0; c < sortedRois.size(); c++) {
					if(sortedRois.get(c) == null) {
						break;
					} else {
						if(tempRoi.equals(sortedRois.get(c))) {
							included = true;
							break;
						} 
					}

				}

				if(!included) {
					roisInEachFrame[currImg].add(tempRoi);
				}
			}
		}
	}

	/**
	 * This method will sort the raw data collected after tracking method is complete to sort by filament number
	 * @param csvFile this is the csv file that needs to be sorted by filament number
	 * @throws IOException
	 */
	private void Sort (File csvFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(csvFile));
		Map<Integer, List<String>> map = new TreeMap<Integer, List<String>>();

		String line = reader.readLine();//read header
		while ((line = reader.readLine()) != null) {
			Integer key = getField(line);
			List<String> l = map.get(key);
			if (l == null) {
				l = new LinkedList<String>();
				map.put(key, l);
			}
			l.add(line);

		}
		reader.close();


		FileWriter writer = new FileWriter(newFileLocation + "\\" + actinFile.getName() + "_sorted_output.csv");
		writer.write("FrameNo,Filament,Area,NormalizedAvgActin,NormalizedAvgABP,ABP/Act,AvgRatio,"
				+ "StandarDev,FrameNo,TotABPInten" + "," + "," +"Number of Filaments\n");
		for (List<String> list : map.values()) {
			for (String val : list) {
				writer.write(val);
				writer.write("\n");
			}
		}
		writer.close();
	}


	private int getField(String line) {
		String numConv = line.split(",")[1];
		int key = Integer.parseInt(numConv);
		return key;// extract value you want to sort on
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		return NO_IMAGE_REQUIRED;
	}

	@Override
	public void setNPasses(int nPasses) {
	}

	protected class Intensities{
		public double averageActBack;
		public double averageABPBack;
		public double averageAct;
		public double averageABP;
		public double areaOutsideROIs;
		public double areaInsideROIs;
		public double normAvgAct;
		public double normAvgABP;

		public double ratio;

		/**
		 * A subclass designed to store all the necessary data (intensity values) for a particular roi in a movie
		 * @param actinBackgroundIntensity The sum of all intensity values in the background box generated for this particular roi in the actin channel
		 * @param ABPBackgroundIntensity The sum of all intensity values in the background box generated for this particular roi in the ABP channel
		 * @param actinIntensity The sum of all intensity values for this particular roi in the actin channel
		 * @param ABPIntensity The sum of all intensity values for this particular roi in the ABP channel
		 * @param totPixelsOutside The area of the background box that does not include any of the rois 
		 * @param totPixelsInside The area of this particular roi 
		 */
		public Intensities(double actinBackgroundIntensity, double ABPBackgroundIntensity, double actinIntensity, double ABPIntensity,  
				double totPixelsOutside,int totPixelsInside) {

			areaOutsideROIs = totPixelsOutside;
			areaInsideROIs = totPixelsInside;
			averageActBack = actinBackgroundIntensity/areaOutsideROIs;
			averageABPBack = ABPBackgroundIntensity/areaOutsideROIs;
			averageAct = actinIntensity/areaInsideROIs;
			averageABP = ABPIntensity/areaInsideROIs;

			normAvgAct = averageAct - averageActBack;

			if(doRatiobg) {
				normAvgABP = averageABP/averageABPBack;
			} else {
				normAvgABP = averageABP - averageABPBack;
			}

			ratio = normAvgABP/normAvgAct;
		}

		@SuppressWarnings("null")
		public Intensities() {
			areaOutsideROIs = (Double) null;
			areaInsideROIs = (Double) null;
			averageActBack = (Double) null;
			averageABPBack = (Double) null;
			averageAct = (Double) null;
			averageABP = (Double) null;

		}

	}

	/**
	 * Subclass that holds the coordinates of the center of a particular roi.
	 * @author santiagoespinosa
	 *
	 */
	protected static class Centroid {
		public double x;
		public double y;

		public Centroid(double X, double Y) {
			x = X;
			y = Y;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public String toString() {
			return "X: " + getX() + "     " + "Y: " + getY(); 
		}


	}

	/**
	 * This method computes the euclidean distance between two points on a 2D image
	 * @param first The first point 
	 * @param second The second point
	 * @return The distance between the two points 
	 */
	private double distance (Centroid first, Centroid second) {

		double ycoord = Math.abs (first.y - second.y);
		double xcoord = Math.abs (first.x - second.x);    
		double distance = Math.sqrt(((ycoord)*(ycoord)) + ((xcoord)*(xcoord)));

		return distance; 
	}

	/**
	 * This program calculates the standard deviation of a list of ratio values
	 * @param mean The mean of the list of ratio values
	 * @param values The list of ratio values 
	 * @return The single standard deviation value of the list of ratio values it was given
	 */
	private double StandardDev (Double mean, double[] values) {
		double sd = 0;
		int length = 0;

		for(double num: values) {
			if(num != 0) {
				sd += Math.pow(num - mean, 2);
				length++;
			}
		}

		sd = Math.sqrt(sd/(length-1));

		return sd; 
	}

	/**
	 * Generates a copy of the Arraylist it was passed as a parameter
	 * @param original The Arraylist of rois that needs to be copied
	 * @return A copy of the original Arraylist of rois 
	 */
	private ArrayList<Roi> copy(ArrayList<Roi> original){

		ArrayList<Roi> copy = new ArrayList<Roi>(original.size());

		for(Roi roi: original) {
			copy.add(roi);
		}

		return copy; 
	}

	/**
	 * Subclass that holds the position (column and row index) of an Roi
	 * @author santiagoespinosa
	 *
	 */
	protected static class Position {
		public int C; // column index which represents a particular frame in a movie
		public int R; // row index which represents a particular roi across all the frames in a movie

		public Position(int c, int r) {
			C = c;
			R = r;
		}

		public int getPositionC () {
			return C;
		}

		public int getPositionR () {
			return R;
		}
	}

}
