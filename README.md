# Vglut2_GluR2_GFP_Fiber

**Developed for:** Maëla

**Team:** Selimi

**Date:** July 2022

**Software:** Fiji

### Images description

3D images taken with a x60 objective

2 channels:
  1. *CSU_488:* VGlut2
  2. *CSU_568:* GluR2
  2. *CSU_647:* GFP Fiber


### Plugin description

* Detect Vglut2 dots with DOG (4-6) + Moments threshold
* Detect GluR2 dots with Stardist model fociRNA-1.2 (probTh = 0.2)
* Detect GFP Fiber with DOG (6-10) + Triangle threshold
* Find VGlut2 fiber+ / Fiber-
* Associate each Vglut with GluR2 at less than 200nm (synapse) for each GluR2 give volume/distance
* One GluR2 can be associated to one Vglut2 


### Dependencies

* **3DImageSuite** Fiji plugin

* **Stardist** model named *fociRNA-1.2.model*
 

### Version history

Version 1 released on July 12, 2023.
