package edu.ucla.cens.mobilize.client.presenter;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.rpc.AsyncCallback;

import edu.ucla.cens.mobilize.client.common.RoleClass;
import edu.ucla.cens.mobilize.client.dataaccess.DataService;
import edu.ucla.cens.mobilize.client.model.ClassInfo;
import edu.ucla.cens.mobilize.client.model.UserInfo;
import edu.ucla.cens.mobilize.client.model.UserShortInfo;
import edu.ucla.cens.mobilize.client.ui.ErrorDialog;
import edu.ucla.cens.mobilize.client.utils.AwErrorUtils;
import edu.ucla.cens.mobilize.client.view.ClassView;

public class ClassPresenter implements ClassView.Presenter, Presenter {
  private ClassView view;
  private UserInfo userInfo;
  private DataService dataService;
  private EventBus eventBus;
  
  
  // logging
  private static Logger _logger = Logger.getLogger(ClassPresenter.class.getName());

  public ClassPresenter(UserInfo userInfo, DataService dataService, EventBus eventBus) {
    this.userInfo = userInfo;
    this.dataService = dataService;
    this.eventBus = eventBus;
  }
  
  public void setView(ClassView classView) {
    this.view = classView;
  }

  
  @Override
  public void go(Map<String, String> params) {
    // hide any leftover notifications
    this.view.hideMsg();
    
    // get subview from url params
    if (params.isEmpty()) {
      this.showClasses();
    } else if (params.get("v").equals("detail") && params.containsKey("id")) {
      // anything after first id is ignored
      this.fetchAndShowClassDetail(params.get("id"));
    } else {
      _logger.warning("Unrecognized subview: " + params.get("v"));
    }
  }

  private void fetchAndShowClassDetail(String classId) {
    dataService.fetchClassDetail(classId, new AsyncCallback<ClassInfo>() {

      @Override
      public void onFailure(Throwable caught) {
        _logger.fine(caught.getMessage());
        view.showListSubview();
        view.showError("There was a problem retrieving the class data.");
        AwErrorUtils.logoutIfAuthException(caught);
      }

      @Override
      public void onSuccess(ClassInfo result) {
        view.setDetail(result);
        view.showDetailSubview();
        fetchAndShowClassMembers(result.getClassId(), result.getUsernameToRoleMap());
      }
    });
  }
  
  private void fetchAndShowClassMembers(String classId, final Map<String, RoleClass> usernameToRoleMap) {
    dataService.fetchClassMembers(classId, new AsyncCallback<List<UserShortInfo>>() {
      @Override
      public void onFailure(Throwable caught) {
        _logger.severe(caught.getMessage());
        view.clearClassMembers();
        ErrorDialog.show("There was a problem retrieving class member info.",
                          caught.getMessage());
      }

      @Override
      public void onSuccess(List<UserShortInfo> result) {
        view.setDetailClassMembers(result, usernameToRoleMap);
      }
    });
  }
  
  // display a list of class names that link to class detail pages
  private void showClasses() { 
    view.setList(userInfo.getClasses());
    view.showListSubview();
  }

}
