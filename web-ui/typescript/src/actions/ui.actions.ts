export const setApplicationVersion = (version:string) => ({
    type: 'SET_APPLICATION_VERSION',
    version
});

export const setAnnounce = (announce:string) => ({
    type: 'SET_ANNOUNCE',
    announce
});

export const setAnnounceOptOut = (announceOptOut:string) => ({
    type: 'SET_ANNOUNCE_OPTOUT',
    announceOptOut
});

export const setAllowEnriched = (allowEnriched:boolean) => ({
    type: 'SET_ALLOW_ENRICHED',
    allowEnriched
});

export const showLibrary = () => ({
    type: 'SHOW_LIBRARY'
});

export const showEditor = () => ({
    type: 'SHOW_EDITOR'
});