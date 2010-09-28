Plot[Sin[x], {x, -5, 5}]

Plot3D[Sin[x] Sin[y], {x, -5, 5}, {y, -5, 5}]

Plot[Sin[x] E^(-x/4), {x, 0, 10}, Filling -> Axis]

Plot[{Sin[x] E^(-x/4), Sin[x], E^(-x/4)}, {x, 0, 10}, Filling -> Axis]

Table[Plot[Sin[x y] E^(-x/4), {x, 0, 10}, PlotRange -> {-1, 1}], {y, 0, 5, 0.1}]

Pi
Sqrt[4]
x = 3;

data = {};
AppendTo[data, "some data"];

?System`*
