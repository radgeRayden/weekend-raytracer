Path-tracer based on the book [RayTracing in One Weekend](https://raytracing.github.io/books/RayTracingInOneWeekend.html), written in [Scopes](http://scopes.rocks).

The output looks almost exactly as the reference implementation:
![output of the raytracer](https://raw.githubusercontent.com/radgeRayden/weekend-raytracer/master/screenshot.png)

To run it, it's necessary to initialize submodules recursively, run `make` in `raydEngine/foreign` and have `radlib` in your `scopes/lib/scopes` folder. Honestly I didn't put much effort in making it easy to run outside my machine, sorry! (also only linux supported atm).
