import { EnrichedPackMetadata } from "./enrichedPackMetadata";

export interface LibraryPack{
  uuid: string;
  packs: Pack[];
}

export interface Pack{
  format: string;
  uuid: string;
  version: number;
  path: string;
  timestamp: number;
  ageMin: number;
  ageMax: number;
  nightModeAvailable: boolean;
  title: string;
  description: string;
  image: string;
  official: boolean;
}

export interface StoryPack {
  uuid: string;
  factoryDisabled: boolean;
  version: number;
  stageNodes: StageNode[];
  enriched: EnrichedPackMetadata;
  nightModeAvailable: boolean;
}

export interface DevicePackInfos {
  uuid: string;
  folderName: string;
  version: number;
  sizeInBytes: number;
  nightModeAvailable: boolean;
  ageMin: number;
  ageMax: number;
}