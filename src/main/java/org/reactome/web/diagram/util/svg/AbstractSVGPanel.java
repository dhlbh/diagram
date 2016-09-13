package org.reactome.web.diagram.util.svg;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.ui.AbsolutePanel;
import org.reactome.web.diagram.util.svg.events.SVGPanZoomEvent;
import org.vectomatic.dom.svg.*;
import org.vectomatic.dom.svg.utils.SVGConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kostas Sidiropoulos <ksidiro@ebi.ac.uk>
 */
public abstract class AbstractSVGPanel extends AbsolutePanel {
    protected EventBus eventBus;

    protected static final float FRAME = 40;

    protected OMSVGSVGElement svg;
    protected List<OMSVGElement> svgLayers;

    protected OMSVGMatrix ctm;
    protected OMSVGMatrix initialTM;
    protected OMSVGRect initialBB;
    protected float zFactor = 1;

    private StringBuilder sb;

    public AbstractSVGPanel(EventBus eventBus) {
        this.eventBus = eventBus;
        sb = new StringBuilder();
    }

    protected void applyCTM(boolean fireEvent) {
        sb.setLength(0);
        sb.append("matrix(").append(ctm.getA()).append(",").append(ctm.getB()).append(",").append(ctm.getC()).append(",")
                .append(ctm.getD()).append(",").append(ctm.getE()).append(",").append(ctm.getF()).append(")");
        for (OMSVGElement svgLayer : svgLayers) {
            svgLayer.setAttribute(SVGConstants.SVG_TRANSFORM_ATTRIBUTE, sb.toString());
        }
        zFactor = ctm.getA();

        if(fireEvent) {
            OMSVGPoint from = svg.createSVGPoint();
            from.setX(0);
            from.setY(0);
            from = from.matrixTransform(ctm.inverse());

            OMSVGPoint to = svg.createSVGPoint();
            to.setX(getOffsetWidth());
            to.setY(getOffsetHeight());
            to = to.matrixTransform(ctm.inverse());

            eventBus.fireEventFromSource(new SVGPanZoomEvent(from, to), this);
        }
    }

    protected OMSVGMatrix calculateFitAll(final float frame){
        OMSVGRect bb = svg.createSVGRect();
        // Add a frame around the image
        bb.setX(initialBB.getX() - frame);
        bb.setY(initialBB.getY() - frame);
        bb.setWidth(initialBB.getWidth() + (frame * 2));
        bb.setHeight(initialBB.getHeight() + (frame * 2));

        float rWidth = getOffsetWidth() / bb.getWidth();
        float rHeight = getOffsetHeight() / bb.getHeight();
        float zoom = (rWidth < rHeight) ? rWidth : rHeight;

        float vpCX = getOffsetWidth() * 0.5f;
        float vpCY = getOffsetHeight() * 0.5f;

        float newCX = bb.getX() + (bb.getWidth()  * 0.5f);
        float newCY = bb.getY() + (bb.getHeight() * 0.5f);

        float corX = vpCX/zoom - newCX;
        float corY = vpCY/zoom - newCY;

        return svg.createSVGMatrix().scale(zoom).translate(corX, corY);
    }

    protected OMSVGPoint getCentrePoint() {
        OMSVGPoint p = svg.createSVGPoint();
        p.setX(getOffsetWidth()/2);
        p.setY(getOffsetHeight()/2);

        return p.matrixTransform(ctm.inverse());
    }

    protected OMSVGPoint getTranslatedPoint(MouseEvent event) {
        OMSVGPoint p = svg.createSVGPoint();
        p.setX(event.getX());
        p.setY(event.getY());

        return p.matrixTransform(ctm.inverse());
    }

    protected List<OMSVGElement> getRootLayers() {
        // Identify all layers by getting all top-level g elements
        List<OMSVGElement> svgLayers = new ArrayList<>();
        OMNodeList<OMNode> cNodes = svg.getChildNodes();
        for (OMNode node : cNodes) {
            if(node instanceof OMSVGGElement) {
                svgLayers.add((OMSVGGElement) node);
            }
        }
        return svgLayers;
    }

    public void setSize(int width, int height) {
        //Set the size of the panel
        setWidth(width + "px");
        setHeight(height + "px");
        //Set the size of the SVG
        if(svg != null) {
            svg.setWidth(Style.Unit.PX, width);
            svg.setHeight(Style.Unit.PX, height);
        }

    }
}
