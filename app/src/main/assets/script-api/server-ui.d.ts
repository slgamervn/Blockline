/** Bundled offline subset; refresh in Blockline to load the selected npm package version. */
import type { Player } from "@minecraft/server";
export declare class ActionFormData {
    /** Sets the title displayed at the top of the form. */
    title(titleText: string): ActionFormData;
    /** Sets the body text displayed in the form. */
    body(bodyText: string): ActionFormData;
    /** Adds an option button. */
    button(text: string, iconPath?: string): ActionFormData;
    /** Displays the form to a player. */
    show(player: Player): Promise<ActionFormResponse>;
}
export declare class MessageFormData {
    /** Sets the form title. */
    title(titleText: string): MessageFormData;
    /** Sets the body text. */
    body(bodyText: string): MessageFormData;
    /** Sets the first response button. */
    button1(text: string): MessageFormData;
    /** Sets the second response button. */
    button2(text: string): MessageFormData;
    /** Displays the form to a player. */
    show(player: Player): Promise<MessageFormResponse>;
}
export declare class ModalFormData {
    /** Sets the form title. */
    title(titleText: string): ModalFormData;
    /** Adds a text field. */
    textField(label: string, placeholder: string, defaultValue?: string): ModalFormData;
    /** Adds a toggle. */
    toggle(label: string, defaultValue?: boolean): ModalFormData;
    /** Adds a dropdown. */
    dropdown(label: string, items: string[], defaultValueIndex?: number): ModalFormData;
    /** Displays the form to a player. */
    show(player: Player): Promise<ModalFormResponse>;
}
export declare interface ActionFormResponse {
    canceled: boolean;
    selection: number;
}
export declare interface MessageFormResponse {
    canceled: boolean;
    selection: number;
}
export declare interface ModalFormResponse {
    canceled: boolean;
    formValues: unknown[];
}
