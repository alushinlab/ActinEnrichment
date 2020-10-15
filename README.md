# Introduction:

This plugin was designed to quantify the relative binding of an actin-binding protein (ABP) to actin in two-color TIRF microscopy. With minimal or no modifications, the plugin might be used to study other cytoskeletons imaged with other fluorescence microscopy methods. For additional details, please see: DOI: 10.7554/eLife.62514



## I. Prepare input data:

The plugin takes two single-channel .tif image stacks as input, one for actin and the other for actin-binding protein (ABP). Each stack is a collection of monochromatic images taken over time. The two stacks should be recorded simultaneously in two separate channels. It is crucial that the two .tif stacks have the same number of frames, and for each pair of corresponding frames in the two stacks, the same field of view is imaged in the actin channel and the actin-binding protein channel, respectively.



## II. Install the plugin:

Open the ImageJ GUI. In the menu, use Plugins >> Install... to install the XXXX.jar file.



## III. Process .tif image stacks with the plugin:

(1) In the ImageJ GUI menu, go to Plugins >> ActinEnrichment to start the plugin;

(2) In the first dialogue, "Please select the original actin stack", select the .tif image stack of actin. Press "open";

(3) In the second dialogue, "Please select the original ABP stack", select the .tif image stack of actin-binding protein. Press "open";

(4) In the third dialogue, select/create a folder, where the quantification results ("intermediate files") will be stored. Press "open";

(5) In the fourth, "parameters", dialogue, select/type the parameters you would like to use for data processing. Default parameters are the parameters we used when processing our data.
-Do tracking (Yes/No): whether the program will track filaments over time.
If "Yes", type minimum distance for tracking and minimum number of consecutive apperances.
-Do watershed (Yes/No): whether the program will cut big actin regions into smaller ones. We recommend "No".
-Ratio of ABP background (Yes/No): whether the program will perform background subtraction by dividing the ROI intensities by background intensities. We recommend "No".

(6) Press "OK" to start data processing. Note: do not close any dialogue until you see a "Colocalization Done!" message.



## IV. Data analysis

A successful execution of the plugin will generate three files in the selected folder: RoiSet.zip (the information of all selected "actin regions", i.e., ImageJ ROIs), TrackingDataOutput.csv (all the raw data generated) and sorted_output.csv (sorted data which will be used for data analysis). In a sorted_output.csv file, the intensity ratio ABP/actin is calculated for each frame and subsequently averaged to render a "single-filament intensity ratio" for each tracked actin filament. Other information, like "total ABP intensity in each frame" and "number of actin filament" can be readily accessed in the file.
