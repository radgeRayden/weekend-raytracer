Path-tracer based on the book [RayTracing in One Weekend](https://raytracing.github.io/books/RayTracingInOneWeekend.html), written in [Scopes](http://scopes.rocks).

The output looks almost exactly as the reference implementation:
![output of the raytracer](https://raw.githubusercontent.com/radgeRayden/weekend-raytracer/master/screenshot.png)

It took 7 hours to render on my i7-5500U using 4 threads.

To run it, it's necessary to initialize submodules recursively, run `make; make install` in `raydEngine/foreign` and have `radlib` in your `scopes/lib/scopes` folder. Honestly I didn't put much effort in making it easy to run outside my machine, sorry! (also only linux supported atm).
