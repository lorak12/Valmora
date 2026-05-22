package org.nakii.valmora.module.ui;

import java.util.List;

public class UIConfig {

    private final String scoreboardTitle;
    private final List<String> scoreboardLines;
    private final String actionBarDefault;
    private final String tabHeader;
    private final String tabFooter;

    public UIConfig(String scoreboardTitle, List<String> scoreboardLines,
                    String actionBarDefault, String tabHeader, String tabFooter) {
        this.scoreboardTitle = scoreboardTitle;
        this.scoreboardLines = scoreboardLines;
        this.actionBarDefault = actionBarDefault;
        this.tabHeader = tabHeader;
        this.tabFooter = tabFooter;
    }

    public String getScoreboardTitle()    { return scoreboardTitle; }
    public List<String> getScoreboardLines() { return scoreboardLines; }
    public String getActionBarDefault()   { return actionBarDefault; }
    public String getTabHeader()          { return tabHeader; }
    public String getTabFooter()          { return tabFooter; }
}
