export enum CommitState {
    UNDEFINED = 'UNDEFINED',
    COULD_NOT_BE_RETRIEVED = 'COULD_NOT_BE_RETRIEVED',
    CLEAN = 'CLEAN',
    UNCOMMITTED_CHANGES = 'UNCOMMITTED_CHANGES',
    WANTS_TO_COMMIT = 'WANTS_TO_COMMIT',
    COMMITTING = 'COMMITTING',
}
