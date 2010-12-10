package edu.ucla.cens.AndWellnessVisualizations.client.presenter;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.ui.HasWidgets;

import edu.ucla.cens.AndWellnessVisualizations.client.common.SetModel;
import edu.ucla.cens.AndWellnessVisualizations.client.event.CampaignConfigurationEvent;
import edu.ucla.cens.AndWellnessVisualizations.client.event.CampaignConfigurationEventHandler;
import edu.ucla.cens.AndWellnessVisualizations.client.event.NewDataPointSelectionEvent;
import edu.ucla.cens.AndWellnessVisualizations.client.model.CampaignInfo;
import edu.ucla.cens.AndWellnessVisualizations.client.model.ConfigurationInfo;
import edu.ucla.cens.AndWellnessVisualizations.client.model.PromptInfo;
import edu.ucla.cens.AndWellnessVisualizations.client.model.SurveyInfo;
import edu.ucla.cens.AndWellnessVisualizations.client.model.UserInfo;
import edu.ucla.cens.AndWellnessVisualizations.client.view.DataPointBrowserView;

public class DataPointBrowserPresenter implements Presenter,
        DataPointBrowserView.Presenter<CampaignInfo,ConfigurationInfo,SurveyInfo,PromptInfo> {

    // Standard presenter fields
    private EventBus eventBus;
    private DataPointBrowserView<CampaignInfo,ConfigurationInfo,SurveyInfo,PromptInfo> view;
    
    // Contains all information about the currently logged in user
    private UserInfo userInfo;
    
    // Contains all selection states from the view
    SetModel<CampaignInfo> setCampaign = new SetModel<CampaignInfo>();
    SetModel<ConfigurationInfo> setConfiguration = new SetModel<ConfigurationInfo>();
    SetModel<String> setUserName = new SetModel<String>();
    SetModel<SurveyInfo> setSurvey = new SetModel<SurveyInfo>();
    SetModel<PromptInfo> setDataPoint = new SetModel<PromptInfo>();
    
    private static Logger _logger = Logger.getLogger(DataPointBrowserPresenter.class.getName());
    
    public DataPointBrowserPresenter(EventBus eventBus, 
            DataPointBrowserView<CampaignInfo,ConfigurationInfo,SurveyInfo,PromptInfo> view) {
        this.eventBus = eventBus;
        this.view = view;
        this.view.setPresenter(this);
    }
    
    /**
     * Runs the presenter by attaching the view to the passed container.
     */
    public void go(HasWidgets container) {
        bind();
        container.clear();
        container.add(view.asWidget());
    }

    /**
     * Binds to necessary events on the event bus.
     */
    private void bind() {
        eventBus.addHandler(CampaignConfigurationEvent.TYPE, new CampaignConfigurationEventHandler() {
            // When we receive new campaign configuration information, save locally and load into
            // the attached view
            public void onReceive(CampaignConfigurationEvent event) {
                userInfo = event.getUserInfo();
                receiveCampaignConfiguration(userInfo);
            }
        });
    }
    
    /**
     * Updates the view based on the new configuration information.  Load the new
     * campaign info into the campaign list and user list.  If there is
     * only one campaign or only one user, automatically select those.  If these is only
     * one campaign, populate the survey list from that campaign, select the first survey in 
     * the list, and automatically populate the data point list from the first survey.
     * 
     * @param newConfig The new configuration information.
     */
    private void receiveCampaignConfiguration(UserInfo newConfig) {
        // Clear everything out
        clearModels();
        
        // Reset the view to get ready for new data
        view.resetData();
        
        // Update the campaign list
        view.setCampaignList(newConfig.getCampaignList());
    }

    /**
     * Call when a new campaign has been selected.  Updates the view with
     * new surveys and data points for the new campaign.
     * 
     * @param campaign The selected campaign.
     */
    public void campaignSelected(CampaignInfo campaign) {
        setCampaign.updateSetItem(campaign);
        
        // Update the view with the versions for this new campaign
        view.setConfigurationList(campaign.getConfigurationList());
        
        // Update the view with the users for this campaign
        view.setUserList(campaign.getUserList());
    }
    
    /** 
     * Call when a new campaign configuration has been selected.  Updates the
     * view with new surveys and data points.
     */
    public void configurationSelected(ConfigurationInfo configuration) {
        setConfiguration.updateSetItem(configuration);
        
        // Update the view with the surveys for this configuration
        view.setSurveyList(configuration.getSurveyList());
    }

    /**
     * Call when a new user has been selected.  If a campaign, survey,
     * and data point have also been selected, ask for new data from the server.
     * 
     * @param userName The selected user name.
     */
    public void userSelected(String userName) {
        setUserName.updateSetItem(userName);
        
    }

    public void surveySelected(SurveyInfo survey) {
        setSurvey.updateSetItem(survey);
        
    }

    public void dataPointSelected(PromptInfo dataPoint) {
        setDataPoint.updateSetItem(dataPoint);
        
    }

    /**
     * Sends out a new data request event using the currently selected
     * campaign, user, survey, and prompt id.  Silently fails if one of the
     * above are not selected.
     */
    private void fetchDataFromServer() {
        // Make sure we have all necessary data to access the server.
        if (!setCampaign.isSet() ||
            !setSurvey.isSet() ||
            !setUserName.isSet() ||
            !setDataPoint.isSet()) {
            
            _logger.warning("Attempted to fetch data without all required parameters");
            return;
        }
        
        // Debugging
        if (_logger.isLoggable(Level.FINE)) {
            String campaign = setCampaign.getSetItem().getCampaignName();
            String survey = setSurvey.getSetItem().getSurveyName();
            String userName = setUserName.getSetItem();
            String dataPoint = setDataPoint.getSetItem().getPromptId();
    
            _logger.fine("Sending out data selection event for campaign: " + campaign + 
                    " survey: " + survey + " user: " + userName + " dataPoint: " + dataPoint);
        }
        
        // Fire off the selection event
        eventBus.fireEvent(new NewDataPointSelectionEvent(setCampaign.getSetItem(),
                            setUserName.getSetItem(),
                            setSurvey.getSetItem(),
                            setDataPoint.getSetItem()));
    }
    
    /**
     * Clears out all data currently stored in our models.
     */
    void clearModels() {
        setCampaign.clear();
        setConfiguration.clear();
        setUserName.clear();
        setSurvey.clear();
        setDataPoint.clear();
    }
}