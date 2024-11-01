## trying out ray casting in java ##

ray casting implementation in java inspired by [Lode Vandevenne](https://lodev.org/cgtutor/raycasting.html)
tutorial on ray casting.

I implemented it in java using standard graphics library.

I **got stuck** :confused: in implementing the perspective projection of floor and ceiling. they are not moving in sync with the walls

to try it out run the class `de.rsh.rycst.App`, use vim keys for movement h,j,k,l.

`java 21` is needed.

### what's in it

+ fast raytracing 60fps no issue (see class `de.rsh.game.RayCaster`)
+ generated textures
+ state machine
+ arena allocation
+ some interesing math

### missing & todo
+ perspective projection has to be fixed
+ sprites for (moving) objects
+ nice textures
+ maybe opengl
+ maybe one can turn it into a real wolfenstein alike game