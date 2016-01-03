package org.reactome.web.diagram.legends;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import org.reactome.web.diagram.common.PwpButton;
import org.reactome.web.diagram.data.interactors.raw.RawInteractors;
import org.reactome.web.diagram.events.*;
import org.reactome.web.diagram.handlers.EntityDecoratorSelectedHandler;
import org.reactome.web.diagram.handlers.InteractorsErrorHandler;
import org.reactome.web.diagram.handlers.InteractorsLoadedHandler;
import org.reactome.web.diagram.handlers.InteractorsResourceChangedHandler;
import org.reactome.web.diagram.util.slider.Slider;
import org.reactome.web.diagram.util.slider.SliderValueChangedEvent;
import org.reactome.web.diagram.util.slider.SliderValueChangedHandler;

/**
 * @author Kostas Sidiropoulos <ksidiro@ebi.ac.uk>
 */
public class InteractorsControl extends LegendPanel implements ClickHandler, SliderValueChangedHandler,
        EntityDecoratorSelectedHandler, InteractorsResourceChangedHandler, InteractorsLoadedHandler, InteractorsErrorHandler {
    private static String LOADING_MSG = "Loading interactors...";

    private RawInteractors interactors;
    private boolean interactorsVisible = true;
    private Double threshold = 0.5;

    private Image loadingIcon;
    private InlineLabel message;
    private FlowPanel controlsFP;
    private PwpButton showBurstsBtn;
    private Slider slider;
    private PwpButton closeBtn;

    public InteractorsControl(EventBus eventBus) {
        super(eventBus);

        this.initUI();
        this.displayLoader(true);
        this.setVisible(false);

        this.initHandlers();
    }

    @Override
    public void onClick(ClickEvent event) {
        Object source = event.getSource();
        if (source.equals(this.closeBtn)) {
            //Is safe to do this here (even if there is not loading in progress because that scenario is checked by the loader)
            eventBus.fireEventFromSource(new InteractorsRequestCanceledEvent(), this);
            this.setVisible(false);
        } else if (source.equals(this.showBurstsBtn)) {
            interactorsVisible = !interactorsVisible;
            eventBus.fireEventFromSource(new InteractorsToggledEvent(interactorsVisible), this);
        }
    }

    @Override
    public void onEntityDecoratorSelected(EntityDecoratorSelectedEvent event) {
        if(event.getSummaryItem()==null || !event.getSummaryItem().getType().equals("TR")) {
            return;
        }
        if(!isVisible()){
            this.setVisible(true);
        }
    }

    @Override
    public void onInteractorsError(InteractorsErrorEvent event) {
        if(!isVisible()){
            this.setVisible(true);
        }
        message.setText(event.getMessage());
        this.addStyleName(RESOURCES.getCSS().interactorsControlError());
    }

    @Override
    public void onInteractorsLoaded(InteractorsLoadedEvent event) {
        if(event!=null){
            this.removeStyleName(RESOURCES.getCSS().interactorsControlError());
            interactorsVisible = true;
            interactors = event.getInteractors();
            //TODO A timer is used only for debug reasons
            Timer t = new Timer() {
                @Override
                public void run() {
                    InteractorsControl.this.displayLoader(false);
                    message.setText(interactors.getResource()!=null ? interactors.getResource() : "");
                }
            };
            t.schedule(1000);
        }
    }

    @Override
    public void onInteractorsResourceChanged(InteractorsResourceChangedEvent event) {
        if(!isVisible()){
            this.setVisible(true);
        }
        this.displayLoader(true);
    }

    @Override
    public void onSliderValueChanged(SliderValueChangedEvent event) {
        threshold = event.getPercentage();
        eventBus.fireEventFromSource(new InteractorsFilteredEvent(threshold), this);
    }

    private void initUI(){
        addStyleName(RESOURCES.getCSS().analysisControl());
        addStyleName(RESOURCES.getCSS().interactorsControl());

        this.loadingIcon = new Image(RESOURCES.loader());
        this.loadingIcon.setStyleName(RESOURCES.getCSS().interactorsControlLoadingIcon());

        this.message = new InlineLabel("");
        this.message.setStyleName(RESOURCES.getCSS().interactorsControlMessage());
        this.showBurstsBtn = new PwpButton("Show/Hide interactors", RESOURCES.getCSS().showBursts(), this);
        this.closeBtn = new PwpButton("Close and clear interactors", RESOURCES.getCSS().close(), this);

        this.slider = new Slider(100, 24, threshold);
        this.slider.addSliderValueChangedHandler(this);
        this.slider.setVisible(true);
        this.slider.setStyleName(RESOURCES.getCSS().interactorsControlSlider());

        this.controlsFP = new FlowPanel();
        this.controlsFP.setStyleName(RESOURCES.getCSS().interactorsControlControls());
        this.controlsFP.add(this.showBurstsBtn);
        this.controlsFP.add(this.slider);

        this.add(this.loadingIcon);
        this.add(this.message);
        this.add(this.controlsFP);
        this.add(this.closeBtn);
    }

    private void initHandlers(){
        eventBus.addHandler(EntityDecoratorSelectedEvent.TYPE, this);
        eventBus.addHandler(InteractorsLoadedEvent.TYPE, this);
        eventBus.addHandler(InteractorsErrorEvent.TYPE, this);
        eventBus.addHandler(InteractorsResourceChangedEvent.TYPE, this);
    }

    private void displayLoader(boolean visible){
        loadingIcon.setVisible(visible);
        controlsFP.setVisible(!visible);
        if(visible){
           message.setText(LOADING_MSG);
        }
    }
}
