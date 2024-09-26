package de.rsh.rycst.swingrendering.graphics;
import java.awt.Color;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import de.rsh.rycst.game.*;
import de.rsh.rycst.player.Player;
import de.rsh.rycst.utils.Tuple;
import de.rsh.rycst.utils.Vec2d;
import de.rsh.rycst.utils.RayCaster;

import java.util.function.*;
import java.lang.Math;
import java.util.Optional;



public class Drawing2D {
    private final double NEAR_CLIPPING_PANE_DIST = 0.125/2*Math.sqrt(2);
    private final double FIELD_OF_VIEW_RAD = Math.PI/2;
    public final Vec2d posTopLeftNormalized = new Vec2d(-0.5, 0.5);
    public final Vec2d posBottomRightNormalized = new Vec2d(0.5, -0.5);
    private WorldMap world;
    private Player player;

    private RayCaster rayCaster;

    private JComponent panel;
    private CoordintateTransformer coordintateTransformer;

    public Drawing2D(JComponent panel, WorldMap world, Player player, CoordintateTransformer coordintateTransformer) {
        this.panel = panel;
        this.world = world;
        this.player = player;
        this.coordintateTransformer = coordintateTransformer;
        this.rayCaster = new RayCaster(world.mapWidth, world.mapHeight, coordintateTransformer);
    }

    public double getWidth() {
        return panel.getWidth();
    }

    public double getHeight() {
        return panel.getHeight();
    }

    private Vec2d panelSize() {
        final Vec2d sz = new Vec2d(getWidth(), getHeight());
        return sz;
    }

    private double panelDiameter() {
        final double d = panelSize().len();
        return d;
    }

    private void drawDot(Graphics2D g, Vec2d c, double r) {
        drawPoint(g, coordintateTransformer.normalizedToPanelCoords(c), r*panelDiameter());
    }

    // TODO separate panel fromn world drawing functions
    private void drawPoint(Graphics2D g, Vec2d center, double r) {
        var intR = Math.toIntExact(Math.round(r));
        var h = 2*intR;
        var x = Math.toIntExact(Math.round(center.x() - r));
        var y = Math.toIntExact(Math.round(center.y() - r));
        g.fillOval(x, y, h, h);
    }
    private void drawLine(Graphics2D g, Vec2d p1, Vec2d p2) {
        var p1_ = coordintateTransformer.normalizedToPanelCoords(p1).toInt();
        var p2_ = coordintateTransformer.normalizedToPanelCoords(p2).toInt();
        g.drawLine(p1_.fst(), p1_.snd(), p2_.fst(), p2_.snd());
    }

    private void drawGrid(Graphics2D g, Vec2d from, Vec2d to) {
        var size = to.sub(from);
        double strideY = size.y()/world.mapHeight;
        double strideX = size.x()/world.mapWidth;
        double signY = Math.signum(strideY);
        double signX = Math.signum(strideX);
        for(double y=from.y(); signY*y<=signY*to.y(); y+=strideY) {
            drawLine(g,new Vec2d(from.x(), y), new Vec2d(to.x(), y));
        }
        for(double x=from.x(); signX*x<=signX*to.x(); x+=strideX) {
            drawLine(g, new Vec2d(x, from.y()), new Vec2d(x,to.y()));
        }
    }

    /**
     * draws the walls and the player and the clipping planes an the view frustrum into a minature 
     * the minature is placed within from and to rectangle field
     * @param g
     * @param from top left normalized coordinates of the minimap area
     * @param to bottom right normalized coordinates of the minimap area
     */
    public void draw(Graphics2D g, Vec2d from, Vec2d to) {
        final var size = to.sub(from);
        drawBackground(g, from, to);
        g.setColor(new Color(0xff,0xff,0xff,255));
        drawGrid(g, from, to);
        var saveColor = g.getColor();
        double xStep = size.x()/WorldMap.mapWidth;
        double yStep = size.y()/WorldMap.mapHeight;
        double xSign = Math.signum(xStep);
        double ySign = Math.signum(yStep);
        var tileSize = coordintateTransformer.normalizedToPaneSize(new Vec2d(xStep, yStep).abs()).toInt();
        double gy = from.y();
        for(int y=0; y<WorldMap.mapHeight; y++) {
            double gx = from.x();
            for(int x=0; x<WorldMap.mapWidth; x++) {
                var tile = world.map[y][x];
                switch (tile) {
                    case WorldMap.SPACE:
                        g.setColor(new Color(Color.TRANSLUCENT));
                        break;
                    case WorldMap.COTTAGEWALL:
                        g.setColor(Color.GREEN);
                        break;
                    case WorldMap.HOUSEWALL:
                        g.setColor(Color.WHITE);
                        break;
                    case WorldMap.OUTERWALL:
                        g.setColor(Color.RED);
                        break;
                    case WorldMap.PILAR:
                        g.setColor(Color.BLUE);
                        break;
                    case WorldMap.TREASURE:
                        g.setColor(Color.YELLOW);
                        break;
                
                    default:
                        throw new IllegalArgumentException(String.format("%d: unknown tile Value at (%d,%d)", tile, x, y));
                }

                if(tile != WorldMap.SPACE) {
                    var tilePos = coordintateTransformer.normalizedToPanelCoords(new Vec2d(gx, gy)).toInt();
                    g.fillRect(tilePos.fst(), tilePos.snd(), tileSize.fst(), tileSize.snd());
                } else {
                    //System.out.println("space");
                }
                gx += xSign*xStep;
            }
            gy -= ySign*yStep;
        }
        g.setColor(saveColor);

        Function<Vec2d,Vec2d> translateToMinimapPos = p -> {
            var pRel = new Vec2d(0.5 + p.x(), 0.5 - p.y());
            var pRelScaled = pRel.mul(size);
            var res = from.add(pRelScaled);
            return res;
        };
        var minimapPlayerPos =  translateToMinimapPos.apply(player.getPos());
        drawDot(g, minimapPlayerPos, 0.005);


        // TODO reuse the 3D calculation!!!
        var playerRay = player.getDir().normalized().scaled(NEAR_CLIPPING_PANE_DIST).movedBy(player.getPos());
        var nearClipPaneLen = Math.tan(FIELD_OF_VIEW_RAD/2.0)*NEAR_CLIPPING_PANE_DIST;
        var nearClipCw = player.getDir().rotateOrthogonallyClockwize().normalized().scaled(nearClipPaneLen);
        var nearClipCCw = player.getDir().rotateOrthogonallyCounterClockwize().normalized().scaled(nearClipPaneLen);

        var mmPlayerRay = translateToMinimapPos.apply(playerRay);
        drawLine(g, minimapPlayerPos, mmPlayerRay);
        drawLine(g, mmPlayerRay, translateToMinimapPos.apply(playerRay.add(nearClipCCw)));
        drawLine(g, mmPlayerRay, translateToMinimapPos.apply(playerRay.add(nearClipCw)));

        var dir = player.getDir().normalized();
        for(double  a = -Math.PI/4; a <= Math.PI/4; a+=Math.PI/16) {
            var dirRay = dir.turnByAngleOf(a).normalized();
            var rayDestinataion = rayCaster.rayCastToGrid(player.getPos(), dirRay,
                                                xy -> world.map[xy.snd()][xy.fst()] != WorldMap.SPACE
                                               );
            rayDestinataion.ifPresent(rd -> {
                var rs = translateToMinimapPos.apply(rd.fst());
                drawLine(g, minimapPlayerPos, rs );
                drawDot(g, rs , 0.003);
        });
        }
//        var rayDestinataion = rayCastToGrid(player.getPos(), dir);
//        rayDestinataion.ifPresent(rd -> {
//            var rs = translateToMinimapPos.apply(rd.fst());
//            drawLine(g, minimapPlayerPos, rs );
//            drawDot(g, rs , 0.003);
//        });
    }


    private void drawBackground(Graphics2D g, Vec2d from, Vec2d to ) {
        var p1 = coordintateTransformer.normalizedToPanelCoords(from).toInt();
        var p2 = coordintateTransformer.normalizedToPanelCoords(to).toInt();
        var size_ = new Tuple<>(p2.fst()-p1.fst(), p2.snd()-p1.snd());
        g.setColor(new Color(0xbb,0xbb,0xbb,255));
        g.fillRect(p1.fst(), p1.snd(), size_.fst(), size_.snd());
    }

}