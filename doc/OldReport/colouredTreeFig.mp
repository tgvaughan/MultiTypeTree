outputtemplate := "%j-%c.eps";
prologues := 3;
beginfig(1)

numeric u;
u := 1cm;

pair root, leaf[], internal[];

root = (0,0);

leaf0 = (-5u,-10u);
leaf1 = (-1.5u,-10u);
leaf2 = (2.5u,-10u);
leaf3 = (5u,-10u);

internal0 = 0.75[leaf0,root];
internal1 = 0.5[internal0,leaf2];

path edge[];
edge0 := leaf0--root;
edge1 := leaf2--internal0;
edge2 := leaf1--internal1;
edge3 := leaf3--root;

pickup pencircle scaled 0.1u;
draw (point 0 of edge0)--(point 0.5 of edge0) withcolor red;
draw (point 0.5 of edge0)--(point 1 of edge0) withcolor blue;
draw edge2 withcolor green;
draw (point 0 of edge1)--(point 0.25 of edge1) withcolor red;
draw (point 0.25 of edge1)--(point 0.75 of edge1) withcolor green;
draw (point 0.75 of edge1)--(point 1.0 of edge1) withcolor blue;
draw (point 0 of edge3)--(point 0.3 of edge3) withcolor green;
draw (point 0.3 of edge3)--(point 0.6 of edge3) withcolor red;
draw (point 0.6 of edge3)--(point 1.0 of edge3) withcolor blue;

endfig;

beginfig(2);


pickup pencircle scaled 0.1u;
for i=0 upto 3:
		draw edge[i];
endfor;

endfig;

end;
