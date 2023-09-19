import DistributionDrill from "metabase/visualizations/click-actions/drills/DistributionDrill";
import type { QueryClickActionsMode } from "../../types";
import { DefaultMode } from "./DefaultMode";

export const SegmentMode: QueryClickActionsMode = {
  name: "segment",
  clickActions: [...DefaultMode.clickActions, DistributionDrill],
};
