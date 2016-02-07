package org.reactome.web.diagram.client;

import com.google.gwt.event.shared.EventBus;
import org.reactome.web.diagram.data.DiagramContext;
import org.reactome.web.diagram.data.InteractorsContent;
import org.reactome.web.diagram.data.graph.model.GraphObject;
import org.reactome.web.diagram.data.graph.model.GraphPhysicalEntity;
import org.reactome.web.diagram.data.interactors.model.*;
import org.reactome.web.diagram.data.interactors.raw.RawInteractor;
import org.reactome.web.diagram.data.layout.Coordinate;
import org.reactome.web.diagram.data.layout.DiagramObject;
import org.reactome.web.diagram.data.layout.Node;
import org.reactome.web.diagram.data.layout.SummaryItem;
import org.reactome.web.diagram.events.*;
import org.reactome.web.diagram.handlers.DiagramLoadedHandler;
import org.reactome.web.diagram.handlers.DiagramRequestedHandler;
import org.reactome.web.diagram.handlers.InteractorsCollapsedHandler;
import org.reactome.web.diagram.handlers.InteractorsResourceChangedHandler;
import org.reactome.web.diagram.renderers.interactor.InteractorRenderer;
import org.reactome.web.diagram.renderers.interactor.InteractorRendererManager;
import org.reactome.web.diagram.util.MapSet;
import org.reactome.web.diagram.util.interactors.InteractorsLayout;

import java.util.*;

/**
 * @author Antonio Fabregat <fabregat@ebi.ac.uk>
 */
public class InteractorsManager implements DiagramLoadedHandler, DiagramRequestedHandler,
        InteractorsCollapsedHandler, InteractorsResourceChangedHandler {

    private static final int MAX_INTERACTORS = 10;

    private EventBus eventBus;

    private DiagramContext context;
    private String currentResource;

    private DiagramInteractor hovered;

    public InteractorsManager(EventBus eventBus) {
        this.eventBus = eventBus;
        addHandlers();
    }

    private void addHandlers() {
        this.eventBus.addHandler(DiagramLoadedEvent.TYPE, this);
        this.eventBus.addHandler(DiagramRequestedEvent.TYPE, this);
        this.eventBus.addHandler(InteractorsCollapsedEvent.TYPE, this);
        this.eventBus.addHandler(InteractorsResourceChangedEvent.TYPE, this);
    }

    public String getCurrentResource() {
        return currentResource;
    }

    public DiagramInteractor getHovered() {
        return hovered;
    }

    /**
     * In every zoom step the way the elements are drawn (even if they are drawn or not) is defined by the
     * renderer assigned. The most accurate and reliable way of finding out the hovered object by the mouse
     * pointer is using the renderer isHovered method.
     */
    public Collection<DiagramInteractor> getHovered(Coordinate model) {
        List<DiagramInteractor> rtn = new LinkedList<>();
        if (context == null) return rtn;
        Collection<DiagramInteractor> target = context.getInteractors().getHoveredTarget(currentResource, model, context.getDiagramStatus().getFactor());
        for (DiagramInteractor interactor : target) {
            InteractorRenderer renderer = InteractorRendererManager.get().getRenderer(interactor);
            if (renderer.isVisible(interactor) && interactor.isHovered(model)) {
                rtn.add(interactor);
            }
        }
        return rtn;
    }

    public boolean isHighlighted(DiagramInteractor item) {
        return Objects.equals(hovered, item);
    }

    @Override
    public void onDiagramLoaded(DiagramLoadedEvent event) {
        context = event.getContext();
    }

    @Override
    public void onDiagramRequested(DiagramRequestedEvent event) {
        context = null;
    }

    @Override
    public void onInteractorsCollapsed(InteractorsCollapsedEvent event) {
        for (InteractorLink link : context.getInteractors().getInteractorLinks(currentResource)) {
            removeInteractorLink(link);
        }
        eventBus.fireEventFromSource(new InteractorsLayoutUpdatedEvent(), this);
    }

    @Override
    public void onInteractorsResourceChanged(InteractorsResourceChangedEvent event) {
        currentResource = event.getResource();
    }

    public InteractorHoveredEvent setHovered(DiagramInteractor hovered) {
        if (!Objects.equals(this.hovered, hovered)) {
            this.hovered = hovered;
            return new InteractorHoveredEvent(hovered);
        }
        return null;
    }

    public void updateInteractor(InteractorEntity entity) {
        InteractorsContent interactors = context.getInteractors();
        interactors.updateView(currentResource, entity);
        for (InteractorLink link : entity.getLinks()) {
            interactors.updateView(currentResource, link);
        }
    }


    public boolean update(SummaryItem summaryItem, Node hovered) {
        boolean forceDraw = updateSummaryItem(hovered);
        boolean pressed = summaryItem.getPressed() != null && summaryItem.getPressed();
        if (pressed) loadInteractors(hovered);
        else removeInteractors(hovered);
        return forceDraw;
    }

    //Why do we need a layout node? easy... layout! layout! layout! :D
    private void loadInteractors(Node node) {
        InteractorsLayout layoutBuilder = new InteractorsLayout(node);
        GraphPhysicalEntity p = node.getGraphObject();
        InteractorsContent interactors = context.getInteractors();
        List<RawInteractor> rawInteractors = interactors.getRawInteractors(currentResource, p.getIdentifier());

        //Keeping a list of the dynamic interactors will help later to decide the number of visible interactors
        List<RawInteractor> dynamicInteractors = new LinkedList<>();
        MapSet<String, GraphObject> map = context.getContent().getIdentifierMap();
        for (RawInteractor rawInteractor : rawInteractors) {
            String acc = rawInteractor.getAcc();
            //The following line removes resource name prefixes in the accession because the graph do not have them (CHEBI:12345 -> 12345)
            Set<GraphObject> objects = map.getElements(acc.replaceAll("^\\w+[-:_]", ""));
            if (objects != null) {
                //All the static links can be created since they do not clutter the view
                for (GraphObject object : objects) {
                    List<DiagramObject> diagramObjectList = object.getDiagramObjects();
                    if (!diagramObjectList.isEmpty()) {
                        for (DiagramObject nodeTo : diagramObjectList) {
                            InteractorLink link;
                            if (node.equals(nodeTo)) {
                                link = new LoopLink(node, rawInteractor.getId(), rawInteractor.getScore());
                            } else {
                                link = new StaticLink(node, (Node) nodeTo, rawInteractor.getId(), rawInteractor.getScore());
                            }
                            interactors.cache(currentResource, node, link);
                            interactors.addToView(currentResource, link);
                        }
                    } else {
                        // Maybe a part of a complex or a set
                        dynamicInteractors.add(rawInteractor);
                    }
                }
            } else {
                dynamicInteractors.add(rawInteractor);
            }
        }

        //From those that are not visible, we pick the top "allowed" number
        int n = getNumberOfInteractorsToDraw(dynamicInteractors);
        for (int i = 0; i < n; i++) {  //please note that "n" can be increased if the interactors are present in the diagram
            RawInteractor rawInteractor = dynamicInteractors.get(i);

            //In this case the interactor is NOT present in the diagram so we have to create an interactor with its link to the node
            InteractorEntity interactor = getOrCreateInteractorEntity(rawInteractor.getAcc(), rawInteractor.getAlias());

            layoutBuilder.doLayout(interactor, i, n);  //the maximum number of elements is used here for layout beauty purposes

            interactors.cache(currentResource, node, interactor);
            InteractorLink link = interactor.addInteraction(node, rawInteractor.getId(), rawInteractor.getScore());
            interactors.cache(currentResource, node, link);

            //next block (adding to the QuadTree) also needs to be done after the doLayout
            interactors.addToView(currentResource, interactor);
            interactors.addToView(currentResource, link);
        }
        eventBus.fireEventFromSource(new InteractorsLayoutUpdatedEvent(), this);
    }

    private void removeInteractors(Node node) {
        InteractorsContent interactors = context.getInteractors();
        List<InteractorLink> interactions = interactors.getInteractorLinks(currentResource, node);
        for (InteractorLink link : interactions) {
            removeInteractorLink(link);
        }
        eventBus.fireEventFromSource(new InteractorsLayoutUpdatedEvent(), this);
    }

    private void removeInteractorLink(InteractorLink link) {
        InteractorsContent interactors = context.getInteractors();
        if (link instanceof DynamicLink) {
            DynamicLink aux = (DynamicLink) link;
            InteractorEntity entity = aux.getInteractorEntity();
            entity.removeLink(aux);
            if (!entity.isVisible()) interactors.removeFromView(currentResource, entity);
        }
        interactors.removeFromView(currentResource, link);
    }

    private boolean updateSummaryItem(DiagramObject hovered) {
        if (hovered instanceof Node) {
            Node node = (Node) hovered;
            Boolean pressed = node.getInteractorsSummary().getPressed();
            node.getInteractorsSummary().setPressed(pressed == null || !pressed);
            node.getDiagramEntityInteractorsSummary().setPressed(pressed == null || !pressed);
        }
        return true;
    }

    private InteractorEntity getOrCreateInteractorEntity(String acc, String alias) {
        InteractorEntity interactor = context.getInteractors().getInteractorEntity(currentResource, acc);
        if (interactor == null) {
            interactor = new InteractorEntity(acc, alias);
            context.getInteractors().cache(currentResource, interactor);
        }
        return interactor;
    }

    private int getNumberOfInteractorsToDraw(Collection items) {
        if (items == null) return 0;
        int n = items.size();
        return n <= MAX_INTERACTORS ? n : MAX_INTERACTORS;
    }
}
