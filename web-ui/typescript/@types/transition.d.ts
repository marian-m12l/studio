import { ActionNode } from "./action";

export interface Transition {
  actionNode: ActionNode;
  optionIndex: number;
}