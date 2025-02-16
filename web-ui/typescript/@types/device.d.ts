export interface FsDeviceInfos {
  uuid: Uint8Array;
  firmwareMajor: number;
  firmwareMinor: number;
  serialNumber: string;
  sdCardSizeInBytes: number;
  usedSpaceInBytes: number;
  deviceKeyV3: FsDeviceKeyV3;
}

export interface FsDeviceKeyV3 {
  aesKey: Uint8Array;
  aesIv: Uint8Array;
  bt: Uint8Array;
}