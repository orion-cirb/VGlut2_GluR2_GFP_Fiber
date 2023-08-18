# Vglut2_GluR2_GFP_Fiber

* **Developed for:** Maëla
* **Team:** Selimi
* **Date:** August 2023
* **Software:** Fiji

### Images description

3D images taken with a x60 objective

3 channels:
  1. *CSU_488:* VGluT2 dots
  2. *CSU_568:* GluR2 dots
  2. *CSU_647:* GFP neuronal fibers

### Plugin description

* Detect VGluT2 dots with DoG filtering + Moments thresholding or Weka
* Detect GluR2 dots with Stardist
* Detect GFP fibers with DoG filtering + Triangle thresholding or Weka
* Label VGluT2 dots as being GFP+ or GFP-
* Associate each VGluT2 dot with all GluR2 dots closer than 200nm (one GluR2 can only be associated to one VGluT2)

### Dependencies

* **3DImageSuite** Fiji plugin
* **Stardist** model named *fociRNA-1.2.model*
* If used, 2 **Weka pixel classifier** models containing *-VGlut2.model* and  *-GFP.model* in their name
 

### Version history

Version 1 released on August 16, 2023.
