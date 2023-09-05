package VGlut2_GluR2_GFP_Fiber_Tools;

import VGlut2_GluR2_GFP_Fiber_Tools.StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.Measure2Distance;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.geom2.measurementsPopulation.MeasurePopulationColocalisation;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import trainableSegmentation.WekaSegmentation;


/**
 * @author ORION-CIRB
 */
public class Tools {
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    private final String helpUrl = "https://github.com/orion-cirb/VGlut2_GluR2_GFP_Fiber";
    
    private final CLIJ2 clij2 = CLIJ2.getInstance();
    
    String[] chNames = {"VGlut2 dots: ", "GluR2 dots: ", "GFP fiber: "};
    public Calibration cal;
    private double pixVol;

    //GFP fiber and VGlut2 dots detection with Weka
    // Weka
    private boolean weka3D = true;
    // Size filtering
    public double minVGlut2Vol = 0.01;
    public double maxVGlut2Vol = Double.MAX_VALUE;
    public double minGFPVol = 0.05;
    public double maxGFPVol = Double.MAX_VALUE;
    
    // GluR2 dots detection with Stardist
    private final Object syncObject = new Object();
    private final File stardistModelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    private final String stardistOutput = "Label Image"; 
    private final String stardistModel = "fociRNA-1.2.zip";
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private final double stardistOverlapThresh = 0.25;
    private double stardistProbThresh = 0.4;

    // Distance max VGlut2 / GluR2
    double maxDist = 0.2; 
    
    
    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    
    
    /**
     * Flush and close an image
     */
    public void closeImage(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("mcib3d.geom2.Object3DInt");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Check that required StarDist models are present in Fiji models folder
     */
    public boolean checkStardistModels() {
        FilenameFilter filter = (dir, name) -> name.endsWith(".zip");
        File[] modelList = stardistModelsPath.listFiles(filter);
        int index = ArrayUtils.indexOf(modelList, new File(stardistModelsPath+File.separator+stardistModel));
        if (index == -1) {
            IJ.showMessage("Error", stardistModel + " StarDist model not found, please add it in Fiji models folder");
            return false;
        }
        return true;
    }
    
    
    /**
     * Find Weka model in images folder
     */
    public boolean checkWekaModel(String imagesFolder, String modelName) {
        String[] files = new File(imagesFolder).list();
        for (String f: files) {
            if (FilenameUtils.getExtension(f).equals("model") && FilenameUtils.getBaseName(f).equals("classifier-"+modelName)) {
                return true;
            }
        }
        IJ.showMessage("Error", "<html><i>classifier-" + modelName + ".model</i> Weka model not found, please add it in images folder");
        return false;
    }
    
    
    /**
     * Find images extension
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
                case "nd" :
                   ext = fileExt;
                   break;
                case "nd2" :
                   ext = fileExt;
                   break;
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "lsm" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;
                case "tiff" :
                    ext = fileExt;
                    break;
            }
        }
        return(ext);
    }
 
    
    /**
     * Find images in folder
     */
    public ArrayList findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal = new Calibration();
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
    /**
     * Find channels name
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels(String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n).toString();
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelFluor(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelFluor(0, n).toString();
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = meta.getChannelEmissionWavelength(0, n).value().toString();
                break;    
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = meta.getChannelEmissionWavelength(0, n).value().toString();
                break; 
            default :
                for (int n = 0; n < chs; n++)
                    channels[n] = Integer.toString(n);
        }
        return(channels);     
    }
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] channels) {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);

        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        for (int n = 0; n < chNames.length; n++) {
            gd.addChoice(chNames[n], channels, channels[n]);
        }
        
        gd.addMessage("GFP/VGlut2 detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min VGlut2 dot volume (µm3) : ", minVGlut2Vol, 2);
        gd.addNumericField("Min GFP fiber volume (µm3) : ", minGFPVol, 2);
        
        gd.addMessage("GluR2 detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Stardist probability threshold: ", stardistProbThresh, 2);
        gd.addNumericField("VGlut2/GluR2 max distance (µm): ", maxDist, 2);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY calibration (µm): ", cal.pixelHeight, 4);
        gd.addNumericField("Z calibration (µm): ", cal.pixelDepth, 4);
        gd.addHelp(helpUrl);
        gd.showDialog();
        
        String[] chChoices = new String[chNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();

        minVGlut2Vol = gd.getNextNumber();
        minGFPVol = gd.getNextNumber();
        
        stardistProbThresh = gd.getNextNumber();
        maxDist = gd.getNextNumber();

        cal.pixelHeight = cal.pixelWidth = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelHeight*cal.pixelWidth*cal.pixelDepth;
        
        if (gd.wasCanceled())
            return(null);
        return(chChoices);
    } 
    
    
    public void preprocessFiles(ArrayList<String> imageFiles, String processDir, String[] chNames, String[] channels) throws Exception {
        try {
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                options.setSplitChannels(true);
                
                // Preprocess GFP channel
                int indexCh = ArrayUtils.indexOf(chNames, channels[2]);
                options.setCBegin(0, indexCh);
                options.setCEnd(0, indexCh);
                ImagePlus imgGFP = BF.openImagePlus(options)[0];
                imgGFP.setCalibration(cal);
                IJ.saveAs(imgGFP, "Tiff", processDir+rootName+"-GFP.tif");
                closeImage(imgGFP);
                
                // Preprocess VGlut2 channel
                indexCh = ArrayUtils.indexOf(chNames, channels[0]);
                options.setCBegin(0, indexCh);
                options.setCEnd(0, indexCh);
                ImagePlus imgVGlut2 = BF.openImagePlus(options)[0];
                imgVGlut2.setCalibration(cal);
                IJ.saveAs(imgVGlut2, "Tiff", processDir+rootName+"-VGlut2.tif");
                closeImage(imgVGlut2);
            }
        } catch (Exception e) { 
            throw e;
        }
    }
    
    
    /** 
     * Segment image with Weka
     */
    public Objects3DIntPopulation wekaSegmentation(ImagePlus img, String dir, String modelName, double minVol, double maxVol) {
        WekaSegmentation weka = new WekaSegmentation(weka3D);    
        weka.setTrainingImage(img);
        weka.loadClassifier(dir + File.separator + "classifier-" + modelName + ".model");
        weka.applyClassifier(false);
        ImagePlus imgBin = weka.getClassifiedImage();
        weka = null;
        if (WindowManager.getWindow("Log").isShowing())
            WindowManager.getWindow("Log").dispose();
        
        Objects3DIntPopulation pop = labelAndGet3DPopulation(imgBin);  
        sizeFilterPop(pop, minVol, maxVol);
        
        closeImage(imgBin);
        return(pop);
    }
    
    
    /**
     * Return population of 3D objects from binary image
     */
    public Objects3DIntPopulation labelAndGet3DPopulation(ImagePlus img) {
      ImageInt labels = new ImageLabeller().getLabels(ImageHandler.wrap(img));
      labels.setCalibration(cal);
      Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
      return pop;
    }
    
    
    /**
     * Remove objects from population with size outside of given range
     */
    public void sizeFilterPop(Objects3DIntPopulation pop, double minVol, double maxVol) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < minVol) || (new MeasureVolume(p).getVolumeUnit() > maxVol));
    }


    /**
     * Get VGlut2 dots colocalizing with GFP fiber
    */
    public Objects3DIntPopulation findVGlut2GFP(Objects3DIntPopulation vglut2Pop, Objects3DIntPopulation gfpPop) {
        Objects3DIntPopulation vglut2GfpPop = new Objects3DIntPopulation();
        if (vglut2Pop.getNbObjects() != 0 && gfpPop.getNbObjects() != 0) {
            MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(vglut2Pop, gfpPop);
            for (Object3DInt vglut: vglut2Pop.getObjects3DInt()) {
                for (Object3DInt gfp: gfpPop.getObjects3DInt()) {
                    double colocVal = coloc.getValueObjectsPair(vglut, gfp);
                    if (colocVal > 0) {
                        vglut2GfpPop.addObject(vglut);
                        break;
                    }
                }
            }
        }
        vglut2GfpPop.resetLabels();
        return(vglut2GfpPop);
    }
   

    /**
     * Apply StarDist 2D slice by slice
     */
    public Objects3DIntPopulation stardistDetection(ImagePlus img) throws IOException{
        ImagePlus imgIn = new Duplicator().run(img);

        // StarDist
        File starDistModelFile = new File(stardistModelsPath+File.separator+stardistModel);
        StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
        star.loadInput(imgIn);
        star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlapThresh, stardistOutput);
        star.run();

        // Label detections in 3D
        ImagePlus imgLabels = star.getLabelImagePlus();
        if (imgLabels.getNChannels() > 1) imgLabels.setDimensions(1, imgLabels.getNChannels(), 1);
        else if (imgLabels.getNFrames() > 1) imgLabels.setDimensions(1, imgLabels.getNFrames(), 1);
        imgLabels.setCalibration(cal);
        Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels));

        closeImage(imgIn);
        closeImage(imgLabels);   
        return(pop);
    }
    

    /**
     * Find GluR2 dots associated with VGlut2 dots (multithreads)
     */
    public Objects3DIntPopulation findGluR2VGlut2Multithreads(Objects3DIntPopulation vglut2Pop, Objects3DIntPopulation glur2Pop) {
        Objects3DIntPopulation glur2Vglut2Pop = new Objects3DIntPopulation();
        vglut2Pop.getObjects3DInt().stream().forEach(vglut-> {
            AtomicInteger glur2Nb = new AtomicInteger(0);
            ArrayList<Double> glur2Vols = new ArrayList<>();
            glur2Pop.getObjects3DInt().parallelStream()
                .filter(glur -> glur.getType() == 0)
                .filter(glur -> new Measure2Distance(vglut, glur).getValue(Measure2Distance.DIST_BB_UNIT) <= maxDist)
                .forEach(glur -> {
                    glur2Nb.getAndIncrement();
                    glur2Vols.add(new MeasureVolume(glur).getVolumeUnit());
                    glur.setType(1);
                    glur2Vglut2Pop.addObject(glur);
                });
            vglut.setComment(glur2Nb.doubleValue() + "_" + glur2Vols.stream().mapToDouble(Double::doubleValue).sum());
        });
        return(glur2Vglut2Pop);
    }

    
    /**
     * Find total volume of objects in population  
     */
    public double findPopVolume(Objects3DIntPopulation pop) {
        double sumVol = 0;
        for(Object3DInt obj: pop.getObjects3DInt()) {
            sumVol += new MeasureVolume(obj).getVolumeUnit();
        }
        return(sumVol);
    }
    

    /**
     * Draw results
     */
    public void drawResults(Objects3DIntPopulation gfpPop, Objects3DIntPopulation vglut2Pop, Objects3DIntPopulation glur2Pop, ImagePlus img,
            String outDir, String imgName) {
        ImageHandler imgFiber = ImageHandler.wrap(img).createSameDimensions();
        ImageHandler imgVGlut2 = imgFiber.createSameDimensions();
        ImageHandler imgGluR2 = imgFiber.createSameDimensions();
        
        for (Object3DInt gfpObj: gfpPop.getObjects3DInt())
            gfpObj.drawObject(imgFiber, 255);
        vglut2Pop.drawInImage(imgVGlut2);
        for (Object3DInt gluR2Obj : glur2Pop.getObjects3DInt())
            gluR2Obj.drawObject(imgGluR2, 255);
        
        ImagePlus[] imgColors = {imgGluR2.getImagePlus(), imgVGlut2.getImagePlus(), imgFiber.getImagePlus(), img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile1 = new FileSaver(imgObjects);
        ImgObjectsFile1.saveAsTiff(outDir + imgName + ".tif");
        closeImage(imgObjects);
    }
    
}
