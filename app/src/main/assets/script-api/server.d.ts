/** Bundled offline subset; refresh in Blockline to load the selected npm package version. */
export declare class World {
    /** Returns a dimension for an identifier such as overworld or nether. */
    getDimension(dimensionId: string): Dimension;
    /** Returns the active players in the world. */
    getAllPlayers(): Player[];
    /** Looks up an entity by its unique identifier. */
    getEntity(id: string): Entity | undefined;
    /** Gets a scoreboard objective by identifier. */
    getScoreboardObjective(objectiveId: string): ScoreboardObjective | undefined;
}
export declare const world: World;
/** Scheduling, commands, and system-level callbacks. */
export declare class System {
    /** Schedules a callback after a delay in ticks. */
    runTimeout(callback: () => void, tickDelay?: number): number;
    /** Schedules a callback on the next tick. */
    run(callback: () => void): number;
    /** Cancels a previously scheduled callback. */
    clearRun(runId: number): void;
}
export declare const system: System;
export declare interface Vector3 {
    x: number;
    y: number;
    z: number;
}
export declare class Dimension {
    /** Gets a block at a location, when available. */
    getBlock(location: Vector3): Block | undefined;
    /** Runs a command in the dimension. */
    runCommand(commandString: string): CommandResult;
}
export declare class Entity {
    readonly id: string;
    readonly typeId: string;
    readonly nameTag: string;
    readonly location: Vector3;
    /** Returns whether the entity is valid in the current world. */
    isValid(): boolean;
    /** Adds an effect to this entity. */
    addEffect(effectType: string, duration: number, options?: unknown): unknown;
}
export declare class Player extends Entity {
    sendMessage(message: string | { rawtext: unknown[] }): void;
    teleport(location: Vector3, options?: unknown): void;
}
export declare class Block {
    readonly typeId: string;
    readonly location: Vector3;
    setType(type: string): void;
}
export declare interface CommandResult {
    successCount: number;
    statusMessage: string;
}
export declare interface ScoreboardObjective {
    readonly id: string;
    getScore(participant: string | Entity): number | undefined;
}
export declare class ItemStack {
    constructor(typeId: string, amount?: number);
    readonly typeId: string;
    amount: number;
    clone(): ItemStack;
}
