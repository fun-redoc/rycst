package de.rsh.rycst.swingrendering.graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import de.rsh.rycst.game.*;
import de.rsh.rycst.utils.Tuple;
import de.rsh.rycst.utils.Vec2d;

import java.util.function.*;
import java.util.Optional;

public class CoordintateTransformer {
    private Optional<Vec2d> panelMidPoint = Optional.empty(); // will be lasily initialize, because with and hight seem not to be set correctly by swing on contruction

    private JComponent panel;
    private DoubleSupplier zoom;
    private Supplier<Vec2d> pan;

    public CoordintateTransformer(JComponent panel, DoubleSupplier zoom, Supplier<Vec2d> pan) {
        this.panel = panel;
        this.zoom = zoom;
        this.pan = pan;
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

    public double panelDiameter() {
        final double d = panelSize().len();
        return d;
    }

    public static Vec2d fromPoint(Point p) {
        return new Vec2d(p.getX(), p.getY());
    }

    public Vec2d eventWorldCoords(MouseEvent e) {
        var v = fromPoint(e.getPoint());
        var w = panelToNormalizedCoords(v);
        return w;
    }
    /**
     * Translates graphics coordinated (e.g. from mouse events) to nrmalized coordinate system
     * x,y€[-1,1] center (0,0).
     * zoom and pan factors are taken into account.
     * @param v
     * @return normed coordinates x,y€[-1,1] center (0,0)
     */
    public  Vec2d panelToNormalizedCoords(Vec2d v) {
        var f = 1.0 / zoom.getAsDouble(); // origin of zoom is the mid of the canvas
        var canvasPointFlipped = v.flippedHorizontally();
        if(!panelMidPoint.isPresent()) {
            panelMidPoint = Optional.of(new Vec2d(getWidth()/2.0, getHeight()/2.0));
        }
        var mid = panelMidPoint.orElse(new Vec2d(getWidth()/2.0, getHeight()/2.0));
        var midFlipped = mid.flippedVertically();
        var pos = canvasPointFlipped.movedBy(midFlipped);
        var scaled = pos.scaled(f);
        var paned = scaled.sub(pan.get());
        var normalized = new Vec2d(paned.x()/getWidth(), paned.y()/getHeight());
        return normalized;
    }
    /**
     * Translates normalized coordinates x,y€[-1,1] center (0,0)
     * from game simulation into graphics drawing coordinates.
     * zoom and pan factors are taken into account.
     * @param v
     * @return normed coordinates x,y€[-1,1] center (0,0)
     */
    public Vec2d normalizedToPanelCoords(Vec2d v) {
        var w = getWidth();
        var h = getHeight();
        var denormalized = new Vec2d(v.x()*w, v.y()*h);
        var unpan = denormalized.add(pan.get());
        var unscale = unpan.scaled(zoom.getAsDouble());
        if(!panelMidPoint.isPresent()) {
            panelMidPoint = Optional.of(new Vec2d(w/2.0, h/2.0));
        }
        var mid = panelMidPoint.orElse(new Vec2d(w/2.0, h/2.0));
        var midFlipped = mid.flippedVertically();
        var canvasPointFlipped = unscale.movedBy(midFlipped.neg());
        var p = canvasPointFlipped.flippedHorizontally();
        return p;

    }

    /**
     * use for sizes and directions
     * @param v
     * @return
     */
    public Vec2d normalizedToPaneSize(Vec2d v) {
        var denormalized = new Vec2d(v.x()*getWidth(), v.y()*getHeight());
        var unpan = denormalized.add(pan.get());
        var unscale = unpan.scaled(zoom.getAsDouble());
        return unscale;
    }

    public double lerp(double a, double b, double p) {
        return a + p*(b-a);
    }
    public Vec2d normalizedToGrid(Vec2d v, int gridWidth, int gridHeight) {
        var x = lerp(0, gridWidth, (v.x() + 0.5));
        var y = -lerp(0, gridHeight, (v.y()-0.5));
        return new Vec2d(x, y);
    }
    public Tuple<Integer, Integer> normalizedToGrid2(Vec2d v, int gridWidth, int gridHeight) {
        return new Tuple<>(xNormalizedToGrid(v.x(), gridWidth, gridHeight), yNormalizedToGrid(v.y(), gridWidth, gridHeight));
    }
    public int xNormalizedToGrid(double x, int gridWidth, int gridHeight) {
        return (int)lerp(0, gridWidth, (x + 0.5));
    }
    public int yNormalizedToGrid(double y, int gridWidth, int gridHeight) {
        return (int)-lerp(0, gridHeight, (y-0.5));
    }
}