import { ImageAsset } from './image';
import { AudioAsset } from './audio';
import { Transition } from './transition';
import { ControlSettings } from './control';

export interface StageNode {
  uuid: string;
  image: ImageAsset;
  audio: AudioAsset;
  okTransition: Transition;
  homeTransition: Transition;
  controlSettings: ControlSettings;
}