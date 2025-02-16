export interface Packs{
  uuid: string;
  packs: {
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
  }[];
}