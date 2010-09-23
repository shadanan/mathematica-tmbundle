Plot[Sin[x], {x, -5, 5}]

Plot3D[Sin[x] Sin[y], {x, -5, 5}, {y, -5, 5}]

Integrate[Cos[x], x]

pages[[1]]

ShadsFunction[x_] := Block[{}, 
    Plot[Sin[x], {x, -5, 5}]
    Plot[Sin[x] E^-x, {x, -5, 5}]
  ]

ShadsFunction[x]

E

Plot[Sin[x] E^(-x/4), {x, 0, 10}, Filling -> Axis]

E 