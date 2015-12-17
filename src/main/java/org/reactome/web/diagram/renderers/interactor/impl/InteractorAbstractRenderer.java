package org.reactome.web.diagram.renderers.interactor.impl;

import org.reactome.web.diagram.data.interactors.raw.Interactor;
import org.reactome.web.diagram.data.layout.Coordinate;
import org.reactome.web.diagram.renderers.interactor.InteractorRenderer;
import org.reactome.web.diagram.util.AdvancedContext2d;

/**
 * @author Antonio Fabregat <fabregat@ebi.ac.uk>
 */
public abstract class InteractorAbstractRenderer implements InteractorRenderer {

    @Override
    public void draw(AdvancedContext2d ctx, Interactor item, Double factor, Coordinate offset) {

    }

    @Override
    public void drawText(AdvancedContext2d ctx, Interactor item, Double factor, Coordinate offset) {

    }
}
