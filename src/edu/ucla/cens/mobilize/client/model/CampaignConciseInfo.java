package edu.ucla.cens.mobilize.client.model;

import edu.ucla.cens.mobilize.client.common.Privacy;
import edu.ucla.cens.mobilize.client.common.RunningState;
import edu.ucla.cens.mobilize.client.common.UserRoles;

// NOTE: this is a read-only class
public class CampaignConciseInfo {
  private String campaignId;
  private String campaignName;
  private RunningState runningState;
  private Privacy privacy;
  private UserRoles userRoles;
  
  public CampaignConciseInfo(String campaignId,
                             String campaignName,
                             RunningState runningState,
                             Privacy privacy,
                             UserRoles roles) {
    this.campaignId = campaignId;
    this.campaignName = campaignName;
    this.runningState = runningState;
    this.privacy = privacy;
    this.userRoles = roles;
  }
  
  // ******** GETTERS ********
  
  public String getCampaignId() {
    return this.campaignId;
  }
  
  public String getCampaignName() {
    return this.campaignName;
  }
  
  public RunningState getRunningState() {
    return this.runningState;
  }
  
  public Privacy getPrivacy() {
    return this.privacy;
  }
  
  public UserRoles getUserRoles() {
    return this.userRoles;
  }

  //******** PERMISSIONS ********
  
  public boolean userCanViewDetails() {
    return true; // everyone
  }
  
  public boolean userCanEdit() {
    return this.userRoles.author || this.userRoles.supervisor || this.userRoles.admin;
  }
  
  public boolean userCanDelete() {
    return userCanEdit();
  }
  
  public boolean userCanAnalyze() {
    return this.userRoles.analyst || 
           this.userRoles.participant || 
           this.userRoles.supervisor || 
           this.userRoles.admin;
  }

}