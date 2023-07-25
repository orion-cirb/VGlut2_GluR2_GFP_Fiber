/*
 * Find vglut2/GluR2 dots in GFP fiber
 * Author Orion cirb
 */

import VGlut2_GluR2_GFP_Fiber_Tools.Processing;
import VGlut2_GluR2_GFP_Fiber_Tools.VGlut2;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
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

public class VGlut2_GluR2_GFP_Fiber implements PlugIn {
    
    VGlut2_GluR2_GFP_Fiber_Tools.Processing tools = new Processing();
    private final boolean canceled = false;
    private String imageDir = "";    

    @Override
    public void run(String arg) {
        try {
            if (canceled) {
                IJ.showMessage("Plugin canceled");
                return;
            }
            if (!tools.checkInstalledModules()) {
                IJ.showMessage(" Pluging canceled");
                return;
            }
            imageDir += IJ.getDirectory("Choose images directory")+File.separator;
            if (imageDir == null) {
                return;
            }
            
            // Find images with fileExt extension
            String fileExt = tools.findImageType(new File(imageDir));
            
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles == null) {
                return;
            }
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata(); 
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find channels, image calibration
            String[] channels = tools.findChannels(imageFiles.get(0), meta, reader);
            tools.cal = tools.findImageCalib(meta);
            String[] chNames = tools.dialog(channels);
            if (chNames == null) {
                IJ.showMessage("No stardist model found or plugin canceled....");
                 return;
            }
              // Create output folder
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            Date date = new Date();
            String outDirResults = imageDir + File.separator+ "Results_" + dateFormat.format(date) +  File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }           
            
            // Write headers for results files
            FileWriter fileResults = new FileWriter(outDirResults + "results.xls", false);
            BufferedWriter outPutResults = new BufferedWriter(fileResults);
            outPutResults.write("Image name\tVGlut2 dot index\tVGlut2 dot volume (um3)\t#GluR2 dot\tGluR2 dot volume (µm3)\n");
            outPutResults.flush();
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                System.out.println("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);

                // Open VGlut2 channel
                System.out.println("- Analyzing " + chNames[0] + " VGlut2 channel -");
                int indexCh = ArrayUtils.indexOf(channels, chNames[0]);
                ImagePlus imgVGlut2 = BF.openImagePlus(options)[indexCh-1];
                Objects3DIntPopulation vglut2Pop = tools.findDoGObjects(imgVGlut2, tools.VGlut2sigma1, tools.VGlut2sigma2, tools.VGlut2ThMet, tools.minDots, tools.maxDots);
                tools.closeImages(imgVGlut2);
                
                // Open GFP Fiber channel
                System.out.println("- Analyzing " + chNames[2] + " GFP Fiber channel -");
                indexCh = ArrayUtils.indexOf(channels, chNames[2]);
                ImagePlus imgGFP = BF.openImagePlus(options)[indexCh-1];
                Objects3DIntPopulation gfpPop = tools.findDoGObjects(imgGFP, tools.GFPsigma1, tools.GFPsigma2, tools.GFPThMet, tools.minGFP, tools.maxGFP);
                
                // Find VGlut2 colocalized with GFP Fiber
                Objects3DIntPopulation vGlut2GFP = tools.findVGlut2GFP(vglut2Pop, gfpPop);
                System.out.println(vGlut2GFP.getNbObjects() + " VGlut2 associated to GFP fiber");
                
                // Open GluR2 channel
                System.out.println("- Analyzing " + chNames[1] + " GluR2 channel -");
                indexCh = ArrayUtils.indexOf(channels, chNames[1]);
                ImagePlus imgGluR2 = BF.openImagePlus(options)[indexCh-1];
                Objects3DIntPopulation gluR2Pop = tools.stardistDetection(imgGluR2);
                tools.closeImages(imgGluR2);
                
                // find GluR2 associated to VGlut2 
                System.out.println("Finding GluR2 associated to VGlut2 ....");
                Date t0 = new Date();
                ArrayList<VGlut2> VGlut2GluR2Syn = new ArrayList<>();
                Objects3DIntPopulation VGlut2GluR2Pop = tools.findVGlut2GluR2(vGlut2GFP, gluR2Pop, VGlut2GluR2Syn);
                Date t1 = new Date();
                System.out.println("time : " + (t1.getTime() - t0.getTime()) + " ms : " + VGlut2GluR2Pop.getNbObjects() + " GluR2 associated to VGlut2");
                
                // Save images
                tools.saveImages(gfpPop, vGlut2GFP, VGlut2GluR2Pop, imgGFP, outDirResults, rootName);
                tools.closeImages(imgGFP);
                
                // Save results
                for (VGlut2 vglut : VGlut2GluR2Syn) {
                    Object3DInt VGlut2Obj = vglut.VGlut2;
                    double VGlut2ObjVol = new MeasureVolume(VGlut2Obj).getVolumeUnit();
                    outPutResults.write(rootName+"\t"+VGlut2Obj.getLabel()+"\t"+VGlut2ObjVol+"\t"+vglut.params.get("gluR2")+"\t"+vglut.params.get("gluR2Vol")+"\n");
                    outPutResults.flush();
                }
            }

           } catch (IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(VGlut2_GluR2_GFP_Fiber.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("--- All done! ---");
    }
}
