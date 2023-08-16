package VGlut2_GluR2_GFP_Fiber_Tools;


import VGlut2_GluR2_GFP_Fiber_Tools.StardistOrion.StarDist2D;
import de.lighti.clipper.Path;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.Thresholder;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
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
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import trainableSegmentation.WekaSegmentation;



public class Processing {
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    private final CLIJ2 clij2 = CLIJ2.getInstance();
        
    // min size for dots
    public double minDots = 0.007;
    public double minGFP = 0.05;
    // max size for dots
    public double maxDots = Double.MAX_VALUE;
    public double maxGFP = Double.MAX_VALUE;
    // Distance max VGlut2 / GluR2
    double distMax = 0.2; 
    // DOG sigma filter
    public double VGlut2sigma1 = 2;
    public double VGlut2sigma2 = 4;
    public double GFPsigma1 = 6;
    public double GFPsigma2 = 10;
    // Threshold method
    public String VGlut2ThMet = "Moments";
    public String GFPThMet = "Triangle";
    public Calibration cal = new Calibration();
    private double pixVol = 0;
    
    public boolean weka = false;
    private boolean weka3D = true;
    
   // Stardist
    private final Object syncObject = new Object();
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private final double stardistProbThresh = 0.40;
    private final double stardistOverlayThresh = 0.25;
    private final File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    private final String model = "fociRNA-1.2.zip";
    private final String stardistOutput = "Label Image"; 
    
     /**
     * check  installed modules
     * @return 
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
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
            System.out.println("No Image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    public void preprocessFile(String processDir, ArrayList<String> imageFiles, String[] chNames, String[] channels) throws Exception {
        try {
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                options.setSplitChannels(true);
                int indexCh = ArrayUtils.indexOf(chNames, channels[0]);
                options.setCBegin(0, indexCh -1);
                options.setCEnd(0, indexCh-1);
                // Open VGlut2 channel
                ImagePlus imgVGlut2 = BF.openImagePlus(options)[0];
                setCalibration(imgVGlut2);
                IJ.saveAs(imgVGlut2, "Tiff", processDir+rootName+"-VGlut2.tif");
                closeImages(imgVGlut2);
                
                // Open GFP channel
                indexCh = ArrayUtils.indexOf(chNames, channels[2]);
                options.setCBegin(0, indexCh-1);
                options.setCEnd(0, indexCh-1);
                ImagePlus imgGFP = BF.openImagePlus(options)[0];
                setCalibration(imgGFP);
                IJ.saveAs(imgGFP, "Tiff", processDir+rootName+"-GFP.tif");
                closeImages(imgGFP);
            }
        }
        catch (Exception e) { throw e; }
    }
    
    /**
     * Dialog
     */
    public String[] dialog(String[] channels) {
        String[] chNames = {"VGlut2 : ", "GluR2 : ", "GFP Fiber : "};
        String[] thMethods = new Thresholder().methods; 
        if (!findStardistModels())
                return(null);
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 15, 0);
        gd.addImage(icon);
        gd.addMessage("Channels selection", Font.getFont("Monospace"), Color.blue);
        for (int n = 0; n < chNames.length; n++) {
            gd.addChoice(chNames[n], channels, channels[n+1]);
        }
        gd.addCheckbox("Use Weka segmentation : ", weka);
        gd.addMessage("GFP fiber threshold", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Method : ", thMethods, GFPThMet);
        gd.addMessage("Size filter", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min dots size (µm3) : ", minDots, 2);
        gd.addNumericField("Min fiber size (µm3) : ", minGFP, 2);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("xy calibration (µm)", cal.pixelHeight,3);
        gd.addNumericField("z calibration (µm)", cal.pixelDepth,3);
        
        gd.showDialog();
        if (gd.wasCanceled())
            return(null);
        String[] chChoices = new String[chNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        weka = gd.getNextBoolean();
        GFPThMet = gd.getNextChoice();
        
        minDots = gd.getNextNumber();
        minGFP = gd.getNextNumber();
        cal.pixelHeight = gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight;
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelHeight*cal.pixelWidth*cal.pixelDepth;
        return(chChoices);
    } 
    
     /* Median filter 
     * Using CLIJ2
     * @param ClearCLBuffer
     * @param sizeXY
     * @param sizeZ
     */ 
    public ClearCLBuffer median_filter(ClearCLBuffer  imgCL, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.median3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        return(imgCLMed);
    }
    
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param size1
     * @param size2
     * @return imgGauss
     */ 
    public ClearCLBuffer DOG(ClearCLBuffer imgCL, double size1, double size2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, size1, size1, size1, size2, size2, size2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
     /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
     * @param fill 
     */
    public ClearCLBuffer threshold(ClearCLBuffer imgCL, String thMed) {
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        return(imgCLBin);
    }
    
      /*
    Find starDist models in Fiji models folder
    */
    public boolean findStardistModels() {
        boolean ok = false;
        FilenameFilter filter = (dir, name) -> name.endsWith(".zip");
        File[] modelList = modelsPath.listFiles(filter);
        for (int i = 0; i < modelList.length; i++) {
            if (modelList[i].getName().equals(model)) {
                return true;
            }
        }
        return(ok);
    } 
    
     /** 
     * Find objects with DOG method
     * @param img channel
     * @return objects population
     */
    public Objects3DIntPopulation findDoGObjects(ImagePlus img, double sigma1, double sigma2, String thMet, double min, double max) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLDOG = DOG(imgCL, sigma1, sigma2);
        clij2.release(imgCL);
        ClearCLBuffer imgCLBin = threshold(imgCLDOG, thMet); 
        clij2.release(imgCLDOG);
        ImagePlus imgBin = clij2.pull(imgCLBin);
        clij2.release(imgCLBin);
        imgBin.setCalibration(cal);
        Objects3DIntPopulation pop = getPopFromImage(imgBin);
        System.out.println(pop.getNbObjects()+" objects found");
        sizeFilterPop(pop, min, max);
        System.out.println(pop.getNbObjects()+" objects found after size filter");
        closeImages(imgBin);
        return(pop);
    } 
    
    /**
     * Find channels name
     * @param imageName
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs+1];
        channels[0] = "None";
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n+1] = Integer.toString(n);
                    else 
                        channels[n+1] = meta.getChannelName(0, n).toString();
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n+1] = Integer.toString(n);
                    else 
                        channels[n+1] = meta.getChannelName(0, n).toString();
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n+1] = Integer.toString(n);
                    else 
                        channels[n+1] = meta.getChannelFluor(0, n).toString();
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n+1] = Integer.toString(n);
                    else 
                        channels[n+1] = meta.getChannelExcitationWavelength(0, n).value().toString();
                break;    
            default :
                for (int n = 0; n < chs; n++)
                    channels[n+1] = Integer.toString(n);
        }
        return(channels);         
    }
    

    public void setCalibration(ImagePlus imp) {
        imp.setCalibration(cal);
    }
     
    /**
     * Find image calibration
     * @param meta
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta) {
        // read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("x cal = " +cal.pixelWidth+", z cal=" + cal.pixelDepth);
        return(cal);
    }
    
     /**
     * Remove objects with size outside of given range
     */
    public void sizeFilterPop(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
    }
    
    public Objects3DIntPopulation getPopFromImage(ImagePlus img) {
      // label binary images first
      ImageLabeller labeller = new ImageLabeller();
      ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
      labels.setCalibration(cal);
      Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
      return pop;
    }
      
     /**
     * Apply StarDist 2D slice by slice
     * Label detections in 3D
     */
   public Objects3DIntPopulation stardistDetection(ImagePlus img) throws IOException{
       ImagePlus imgIn = new Duplicator().run(img);
       // StarDist
       File starDistModelFile = new File(modelsPath+File.separator+model);
       StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
       star.loadInput(imgIn);
       star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlayThresh, stardistOutput);
       star.run();
       
       // Label detections in 3D
       ImagePlus imgLabels = star.associateLabels();
       imgLabels.setCalibration(cal); 
       ImageInt label3D = ImageInt.wrap(imgLabels);
       // Get objects as a population of objects
       closeImages(imgIn);
       closeImages(imgLabels);        
       Objects3DIntPopulation pop = new Objects3DIntPopulation(label3D);
       label3D.closeImagePlus();
       System.out.println(pop.getNbObjects()+" GluR2 detections");
       
       // Filter objects
       //sizeFilterPop(pop, minDots, maxDots);
       //System.out.println(pop.getNbObjects()+ " GluR2 remaining after size filtering");
       return(pop);
    }  
   
   /**
    * find VGlut2 associated to GFP fiber 
     * @param vglut2Pop
     * @param gfpPop
     * @return 
    */
   public Objects3DIntPopulation findVGlut2GFP(Objects3DIntPopulation vglut2Pop, Objects3DIntPopulation gfpPop) {
       Objects3DIntPopulation VGlut2GFPPop = new Objects3DIntPopulation();
       if (vglut2Pop.getNbObjects() != 0 && gfpPop.getNbObjects() != 0) {
           MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(vglut2Pop, gfpPop);
           for (Object3DInt vglut: vglut2Pop.getObjects3DInt()) {
                for (Object3DInt gfp: gfpPop.getObjects3DInt()) {
                    double colocVal = coloc.getValueObjectsPair(vglut, gfp);
                    if (colocVal != 0) {
                        VGlut2GFPPop.addObject(vglut);
                        break;
                    }
                }
           }
       }
       VGlut2GFPPop.resetLabels();
       return(VGlut2GFPPop);
   }
   
   /**
    * Find GluR2 associated to VGlut2/GFP
    * @param gluR2Pop
     * @param vglut2Pop 
     * @return  
    */
   
   public Objects3DIntPopulation findVGlut2GluR2(Objects3DIntPopulation vglut2Pop, Objects3DIntPopulation gluR2Pop, ArrayList<VGlut2> VGlut2GluR2Syn) {
        Objects3DIntPopulation GluR2PopVGlut = new Objects3DIntPopulation();
        for (Object3DInt vglut : vglut2Pop.getObjects3DInt()) {
            //System.out.println("Doing Vglut " + VGlut2Obj.getLabel());
            double gluR2Nb = 0;
            double gluR2Vol = 0;
            for (Object3DInt gluR2: gluR2Pop.getObjects3DInt()) {
                if (gluR2.getType() == 0) {
                    double dist = new Measure2Distance(vglut, gluR2).getValue(Measure2Distance.DIST_BB_UNIT);
                    if (dist <= distMax) {
                        gluR2Nb++;
                        gluR2Vol += new MeasureVolume(gluR2).getVolumeUnit();
                        GluR2PopVGlut.addObject(gluR2);
                        gluR2.setType(1);
                    }
                }
            }
            VGlut2GluR2Syn.add(new VGlut2(vglut));
            VGlut2GluR2Syn.get(VGlut2GluR2Syn.size() - 1).params.put("gluR2", gluR2Nb);
            VGlut2GluR2Syn.get(VGlut2GluR2Syn.size() - 1).params.put("gluR2Vol", gluR2Vol);
        }
        GluR2PopVGlut.resetLabels();
        return(GluR2PopVGlut);
    }
   
     /**
     * Find Weka model in images folder
     */
    public String findWekaModel(String imagesFolder, String model) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals("model")) {
                if (f.contains(model)) 
                    return(imagesFolder + File.separator + f);
            }
        }
        return "";
    }
    
   
   public Objects3DIntPopulation findVGlut2GluR2Multi(Objects3DIntPopulation vglut2Pop, Objects3DIntPopulation gluR2Pop, ArrayList<VGlut2> VGlut2GluR2Syn) {
        Objects3DIntPopulation GluR2PopVGlut = new Objects3DIntPopulation();
        vglut2Pop.getObjects3DInt().parallelStream().forEach(vglutObj-> {
            System.out.print("Doing Vglut " + vglutObj.getLabel() + " ");
            ArrayList<Double> sumVolumeNeighbors = new ArrayList<>();
            AtomicInteger numNeighbors = new AtomicInteger(0);
            gluR2Pop.getObjects3DInt().stream()
                .filter(glur2Obj -> glur2Obj.getType() == 0)
                .filter(glur2Obj -> new Measure2Distance(vglutObj, glur2Obj).getValue(Measure2Distance.DIST_BB_UNIT) <= distMax)
                .forEach(glur2Obj -> {
                    numNeighbors.getAndIncrement();
                    sumVolumeNeighbors.add(new MeasureVolume(glur2Obj).getVolumeUnit());
                    GluR2PopVGlut.addObject(glur2Obj);
                    glur2Obj.setType(1);
                });
            System.out.println(numNeighbors.get()+" Neighbors found");
            VGlut2GluR2Syn.add(new VGlut2(vglutObj));
            VGlut2GluR2Syn.get(VGlut2GluR2Syn.size() - 1).params.put("gluR2", numNeighbors.doubleValue());
            VGlut2GluR2Syn.get(VGlut2GluR2Syn.size() - 1).params.put("gluR2Vol", sumVolumeNeighbors.stream().mapToDouble(Double::doubleValue).sum());
        });
        return(GluR2PopVGlut);
    }

    /**
     *
     * @param img
     */
    public void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    public void saveImages(Objects3DIntPopulation GFPFiber, Objects3DIntPopulation VGlut2GFP, Objects3DIntPopulation GluR2pop, ImagePlus img,
            String outDir, String imgName) {
        ImageHandler imgFiber = ImageHandler.wrap(img).createSameDimensions();
        for (Object3DInt gfpObj : GFPFiber.getObjects3DInt())
                gfpObj.drawObject(imgFiber, 255);
        ImageHandler imgVGlut2 = imgFiber.createSameDimensions();
        for (Object3DInt vglut : VGlut2GFP.getObjects3DInt()) {
            vglut.drawObject(imgVGlut2, 255);
        }
        ImageHandler imgGluR2 = imgFiber.createSameDimensions();
        for (Object3DInt gluR2Obj : GluR2pop.getObjects3DInt())
                gluR2Obj.drawObject(imgGluR2, 255);
        ImagePlus[] imgColors = {imgGluR2.getImagePlus(), imgVGlut2.getImagePlus(), imgFiber.getImagePlus(), img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile1 = new FileSaver(imgObjects);
        ImgObjectsFile1.saveAsTiff(outDir + imgName + ".tif");
        closeImages(imgObjects);
    }
    
    
    /** Do Weka on image
     * 
     * @param img
     * @param dir
     * @param channel
     * @param model
     * @return 
     */
    public Objects3DIntPopulation goWeka(ImagePlus img, String dir, String channel) {
        String wekaModel = findWekaModel(dir, channel);
        if(wekaModel.equals("")) {
            return(null);
        }
        System.out.println("Model = "+new File(wekaModel).getName());
        WekaSegmentation weka = new WekaSegmentation(weka3D);    
        weka.setTrainingImage(img);
        weka.loadClassifier(wekaModel);
        weka.applyClassifier(false);
        ImagePlus imgRes = weka.getClassifiedImage();
        weka = null;
        if (WindowManager.getWindow("Log").isShowing())
            WindowManager.getWindow("Log").dispose();
        Objects3DIntPopulation pop = getPopFromImage(imgRes);  
        // Remove small objects
        if (channel.equals("GFP"))
            sizeFilterPop(pop, minGFP, maxGFP);
        else
            sizeFilterPop(pop, minDots, maxDots);
        System.out.println(pop.getNbObjects() + " " + channel + " objects found...");
        return(pop);
    }
    
}
