import VGlut2_GluR2_GFP_Fiber_Tools.Tools;
import VGlut2_GluR2_GFP_Fiber_Tools.QuantileBasedNormalization;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureVolume;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;

/**
 * Detect Vglut2 and GluR2 dots on GFP fiber
 * @author ORION-CIRB
 */
public class VGlut2_GluR2_GFP_Fiber implements PlugIn {
    
    VGlut2_GluR2_GFP_Fiber_Tools.Tools tools = new Tools();

    public void run(String arg) {
        try {            
            String imageDir = IJ.getDirectory("Choose images directory")+File.separator;
            if (imageDir == null) {
                tools.print("Specified directory not found");
                return;
            }          
            
            if (!tools.checkInstalledModules() || !tools.checkStardistModels() || !tools.checkWekaModel(imageDir, "GFP") || !tools.checkWekaModel(imageDir, "VGlut2")) {
                return;
            }
            
            // Find images with fileExt extension
            String fileExt = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles.isEmpty()) {
                IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                return;
            }
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata(); 
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find image calibration
            tools.findImageCalib(meta);
            
            // Find channel names
            String[] channels = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Generate dialog box
            String[] chNames = tools.dialog(channels);
            if (chNames == null) {
                tools.print("Plugin canceled");
                return;
            }
            
            // Create output folder for preprocessed images
            String processDir = imageDir + File.separator + "Preprocessed" + File.separator;
            if (!Files.exists(Paths.get(processDir))) {
                new File(processDir).mkdir();
                
                // Preprocess images
                tools.print("--- NORMALIZING IMAGES ---");
                tools.preprocessFiles(imageFiles, processDir, channels, chNames);
                // Normalize GFP channel
                QuantileBasedNormalization qbn = new QuantileBasedNormalization();
                qbn.run(processDir, imageFiles, "-GFP");
                // Normalize VGlut2 channel
                qbn.run(processDir, imageFiles, "-VGlut2");
                tools.print("Normalisation done");
            }
            
            // Create output folder for results files and images
            String outDir = imageDir + File.separator + "Results_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + File.separator;
            if (!Files.exists(Paths.get(outDir))) {
                new File(outDir).mkdir();
            }
            
            // Write headers in results files
            FileWriter fwResults = new FileWriter(outDir + "results.csv", false);
            BufferedWriter results = new BufferedWriter(fwResults);
            results.write("Image name\tGFP fiber total volume(µm3)\tVGlut2 dot label\tVGlut2 dot volume (µm3)\tNb associated GluR2 dots\tAssociated GluR2 dots total volume (µm3)\n");
            results.flush();
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                System.out.println("--- ANALYZING IMAGE " + rootName + " ---");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Detect GFP fiber
                System.out.println("- Analyzing " + chNames[2] + " GFP fiber channel -");
                ImagePlus imgGFP = IJ.openImage(processDir+rootName+"-GFP.tif");
                Objects3DIntPopulation gfpPop = tools.wekaSegmentation(imgGFP, imageDir, "GFP", tools.minGFPVol, tools.maxGFPVol);
                System.out.println(gfpPop.getNbObjects() + " GFP objects found");
                
                // Detect VGlut2 dots
                System.out.println("- Analyzing " + chNames[0] + " VGlut2 dots channel -");
                ImagePlus imgVGlut2 = IJ.openImage(processDir+rootName+"-VGlut2.tif");
                Objects3DIntPopulation vglut2Pop = tools.wekaSegmentation(imgVGlut2, imageDir, "VGlut2", tools.minVGlut2Vol, tools.maxVGlut2Vol);
                System.out.println(vglut2Pop.getNbObjects() + " VGlut2 objects found");
                tools.closeImage(imgVGlut2);
                
                // Find VGlut2 dots colocalizing with GFP fiber
                System.out.println("- Finding VGlut2 dots colocalizing with GFP fiber -");
                Objects3DIntPopulation vglut2GfpPop = tools.findVGlut2GFP(vglut2Pop, gfpPop);
                System.out.println(vglut2GfpPop.getNbObjects() + " VGlut2 dots colocalizing with GFP fiber");
                
                // Detect GluR2 dots
                System.out.println("- Analyzing " + chNames[1] + " GluR2 channel -");
                int indexCh = ArrayUtils.indexOf(channels, chNames[1]);
                ImagePlus imgGluR2 = BF.openImagePlus(options)[indexCh];
                Objects3DIntPopulation glur2Pop = tools.stardistDetection(imgGluR2);
                System.out.println(glur2Pop.getNbObjects() + " GluR2 dots found");
                tools.closeImage(imgGluR2);
                
                // Find GluR2 dots associated with VGlut2 dots
                System.out.println("- Finding GluR2 dots associated with VGlut2 dots -");
                Objects3DIntPopulation glur2Vglut2Pop = tools.findGluR2VGlut2Multithreads(vglut2GfpPop, glur2Pop);
                System.out.println(glur2Vglut2Pop.getNbObjects() + " GluR2 dots associated with VGlut2 dots");
               
                // Write results
                double gfpVol = tools.findPopVolume(gfpPop);
                for (Object3DInt vglut: vglut2GfpPop.getObjects3DInt()) {
                    double vglutVol = new MeasureVolume(vglut).getVolumeUnit();
                    String[] glurParams = vglut.getComment().split("_");
                    results.write(rootName+"\t"+gfpVol+"\t"+vglut.getLabel()+"\t"+vglutVol+"\t"+glurParams[0]+"\t"+glurParams[1]+"\n");
                    results.flush();
                }
                
                // Draw results
                tools.drawResults(gfpPop, vglut2GfpPop, glur2Vglut2Pop, imgGFP, outDir, rootName);
                tools.closeImage(imgGFP);
            }
            System.out.println("--- All done! ---");
        } catch (NullPointerException | IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(VGlut2_GluR2_GFP_Fiber.class.getName()).log(Level.SEVERE, null, ex);
            tools.print("Plugin aborted");
        }
    }
}
