Plot[Sin[x], {x, -5, 5}]

Plot3D[Sin[x] Sin[y], {x, -5, 5}, {y, -5, 5}]

OutputForm[Integrate[Cos[x], x]]

pages[[1]]

ShadsFunction[x_] := Block[{}, 
    Plot[Sin[x], {x, -5, 5}]
    Plot[Sin[x] E^-x, {x, -5, 5}]
  ]

ShadsFunction[x]

E

Plot[Sin[x] E^(-x/4), {x, 0, 10}, Filling -> Axis]

Pi

Plot[{Sin[x] E^(-x/4), Sin[x], E^(-x/4)}, {x, 0, 10}, Filling -> Axis]

%
Out[1]

x = 3

%

Sqrt[4]

x = 3;

data = {};
AppendTo[data, "shad"];

Table[Plot[Sin[x y] E^(-x/4), {x, 0, 10}, PlotRange -> {-1, 1}], {y, 0, 5, 0.1}]