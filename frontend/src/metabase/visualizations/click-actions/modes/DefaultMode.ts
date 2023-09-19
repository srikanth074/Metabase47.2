import { AutomaticInsightsDrill } from "metabase/visualizations/click-actions/drills/AutomaticInsightsDrill";
import UnderlyingRecordsDrill from "metabase/visualizations/click-actions/drills/UnderlyingRecordsDrill";
import { ObjectDetailDrill } from "metabase/visualizations/click-actions/drills/ObjectDetailDrill";
import SortDrill from "metabase/visualizations/click-actions/drills/SortDrill";
import ZoomDrill from "metabase/visualizations/click-actions/drills/ZoomDrill";
import { ColumnFilterDrill } from "metabase/visualizations/click-actions/drills/ColumnFilterDrill";
import type { QueryClickActionsMode } from "../../types";
import { ColumnFormattingAction } from "../actions/ColumnFormattingAction";
import { DashboardClickAction } from "../actions/DashboardClickAction";

export const DefaultMode: QueryClickActionsMode = {
  name: "default",
  clickActions: [
    UnderlyingRecordsDrill,
    ZoomDrill,
    SortDrill,
    ObjectDetailDrill,
    ColumnFilterDrill,
    AutomaticInsightsDrill,
    ColumnFormattingAction,
    DashboardClickAction,
  ],
};
