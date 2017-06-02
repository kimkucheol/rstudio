/*
 * BranchToolbarButton.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.vcs;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rstudio.core.client.MapUtil;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.WidgetHandlerRegistration;
import org.rstudio.core.client.js.JsUtil;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.core.client.widget.CustomMenuItemSeparator;
import org.rstudio.core.client.widget.ScrollableToolbarPopupMenu;
import org.rstudio.core.client.widget.SearchWidget;
import org.rstudio.core.client.widget.ToolbarButton;
import org.rstudio.core.client.widget.ToolbarPopupMenu;
import org.rstudio.studio.client.common.icons.StandardIcons;
import org.rstudio.studio.client.workbench.views.vcs.common.events.VcsRefreshEvent;
import org.rstudio.studio.client.workbench.views.vcs.common.events.VcsRefreshHandler;
import org.rstudio.studio.client.workbench.views.vcs.git.model.GitState;

public class BranchToolbarButton extends ToolbarButton
                                 implements HasValueChangeHandlers<String>,
                                            VcsRefreshHandler
{
   protected class SwitchBranchCommand implements Command
   {
      public SwitchBranchCommand(String branchLabel, String branchValue)
      {
         branchLabel_ = branchLabel;
         branchValue_ = branchValue;
      }

      @Override
      public void execute()
      {
         setBranchCaption(branchLabel_);
         ValueChangeEvent.fire(BranchToolbarButton.this, branchValue_);
      }

      private final String branchLabel_;
      private final String branchValue_;
   }

   @Inject
   public BranchToolbarButton(final Provider<GitState> pVcsState)
   {
      super("",
            StandardIcons.INSTANCE.empty_command(),
            new ScrollableToolbarPopupMenu());
      pVcsState_ = pVcsState;

      setTitle("Switch branch");

      new WidgetHandlerRegistration(this)
      {
         @Override
         protected HandlerRegistration doRegister()
         {
            return pVcsState.get().addVcsRefreshHandler(
                                                BranchToolbarButton.this, true);
         }
      };
      
      menu_ = getMenu();
      menu_.setAutoHideRedundantSeparators(false);
      
      searchWidget_ = new SearchWidget();
      searchWidget_.addValueChangeHandler(new ValueChangeHandler<String>()
      {
         @Override
         public void onValueChange(ValueChangeEvent<String> event)
         {
            lastSearchValue_ = event.getValue();
            searchValueChangeTimer_.schedule(200);
         }
      });
      
      searchValueChangeTimer_ = new Timer()
      {
         @Override
         public void run()
         {
            onSearchValueChange();
         }
      };
   }
   
   public void setBranchCaption(String caption)
   {
      if (StringUtil.isNullOrEmpty(caption))
         caption = NO_BRANCH;
      
      setText(caption);
   }

   @Override
   public HandlerRegistration addValueChangeHandler(ValueChangeHandler<String> handler)
   {
      return addHandler(handler, ValueChangeEvent.getType());
   }

   @Override
   public void onVcsRefresh(VcsRefreshEvent event)
   {
      JsArrayString branches = pVcsState_.get().getBranchInfo().getBranches();
      onRefresh(branches);
   }
   
   private void onRefresh(JsArrayString branches)
   {
      menu_.clearItems();
      
      if (branches.length() == 0)
      {
         onBeforePopulateMenu(menu_);
         populateEmptyMenu(menu_);
         return;
      }
      
      // separate branches based on remote name
      Map<String, List<String>> branchMap = new LinkedHashMap<String, List<String>>();
      List<String> localBranches = new ArrayList<String>();
      branchMap.put(LOCAL_BRANCHES, localBranches);
      for (String branch : JsUtil.asIterable(branches))
      {
         if (branch.startsWith("remotes/"))
         {
            JsArrayString parts = StringUtil.split(branch, "/");
            if (parts.length() > 2)
            {
               String remote = parts.get(1);
               if (!branchMap.containsKey(remote))
                  branchMap.put(remote, new ArrayList<String>());
               List<String> remoteBranches = branchMap.get(remote);
               remoteBranches.add(branch);
            }
         }
         else
         {
            localBranches.add(branch);
         }
      }
      
      // record the branches used on first populate
      initialBranchMap_ = branchMap;
      
      onBeforePopulateMenu(menu_);
      populateMenu(menu_, branchMap);
   }
   
   private Widget separatorWithSearch(Label label)
   {
      label.getElement().getStyle().setFloat(Style.Float.LEFT);
      searchWidget_.getElement().getStyle().setFloat(Style.Float.RIGHT);
      
      FlowPanel container = new FlowPanel();
      container.add(label);
      container.add(searchWidget_);
      return container;
   }
   
   private void populateEmptyMenu(final ToolbarPopupMenu menu)
   {
      menu.addSeparator(new CustomMenuItemSeparator()
      {
         @Override
         public Widget createMainWidget()
         {
            Label label = new Label(NO_BRANCHES_AVAILABLE);
            label.addStyleName(ThemeStyles.INSTANCE.menuSubheader());
            label.getElement().getStyle().setPaddingLeft(2, Unit.PX);
            return separatorWithSearch(label);
         }
      });
   }
   
   private void populateMenu(final ToolbarPopupMenu menu, final Map<String, List<String>> branchMap)
   {
      if (branchMap.isEmpty())
      {
         populateEmptyMenu(menu);
         return;
      }
      
      MapUtil.forEach(branchMap, new MapUtil.ForEachCommand<String, List<String>>()
      {
         int separatorCount_ = 0;
            
         @Override
         public void execute(final String caption, final List<String> branches)
         {
            // place commonly-used branches at the top
            Collections.sort(branches, new Comparator<String>()
            {
               private final String[] specialBranches_ = new String[] {
                     "master",
                     "develop",
                     "trunk"
               };
               
               @Override
               public int compare(String o1, String o2)
               {
                  for (String specialBranch : specialBranches_)
                  {
                     if (o1.endsWith(specialBranch))
                        return -1;
                     else if (o2.endsWith(specialBranch))
                        return 1;
                  }
                  
                  return o1.compareToIgnoreCase(o2);
               }
            });
            
            menu.addSeparator(new CustomMenuItemSeparator()
            {
               @Override
               public Widget createMainWidget()
               {
                  String branchLabel = caption.equals(LOCAL_BRANCHES)
                        ? LOCAL_BRANCHES
                        : "(Remote: " + caption + ")";
                  Label label = new Label(branchLabel);
                  label.addStyleName(ThemeStyles.INSTANCE.menuSubheader());
                  label.getElement().getStyle().setPaddingLeft(2, Unit.PX);
                  
                  Widget mainWidget = (separatorCount_++ == 0)
                        ? separatorWithSearch(label)
                        : label;
                  
                  return mainWidget;
               }
            });
            menu.addSeparator();
            
            for (String branch : branches)
            {
               // skip detached branches
               if (branch.contains("HEAD detached at"))
                  continue;

               // skip HEAD branches
               if (branch.contains("HEAD ->"))
                  continue;
               
               // construct branch label without remotes prefix

               final String branchLabel = branch.replaceAll("^remotes/" + caption + "/", "");
               final String branchValue = branch.replaceAll("\\s+\\-\\>.*", "");
               menu.addItem(new MenuItem(
                     branchLabel,
                     new SwitchBranchCommand(branchLabel, branchValue)));
            }
         }
      });
   }

   protected void onBeforePopulateMenu(ToolbarPopupMenu rootMenu)
   {
   }
   
   private void onSearchValueChange()
   {
      // fast path -- no query to use
      String query = lastSearchValue_.trim();
      if (query.isEmpty())
      {
         onBeforePopulateMenu(menu_);
         populateMenu(menu_, initialBranchMap_);
      }
      
      // iterate through initial branch map and copy branches
      // matching the current query. TODO: should we re-order
      // based on how 'close' a match we have?
      Map<String, List<String>> branchMap = new HashMap<String, List<String>>();
      for (String key : initialBranchMap_.keySet())
      {
         List<String> filteredBranches = new ArrayList<String>();
         List<String> branches = initialBranchMap_.get(key);
         for (String branch : branches)
            if (branch.indexOf(query) != -1)
               filteredBranches.add(branch);
         
         if (!filteredBranches.isEmpty())
            branchMap.put(key, filteredBranches);
      }
      
      // re-populate
      menu_.clearItems();
      onBeforePopulateMenu(menu_);
      populateMenu(menu_, branchMap);
   }

   protected final Provider<GitState> pVcsState_;
   
   private final ToolbarPopupMenu menu_;
   private final SearchWidget searchWidget_;
   private Map<String, List<String>> initialBranchMap_;
   
   private String lastSearchValue_;
   private final Timer searchValueChangeTimer_;

   private static final String NO_BRANCH = "(no branch)";
   private static final String NO_BRANCHES_AVAILABLE = "(no branches available)";
   private static final String LOCAL_BRANCHES = "(local branches)";
}
