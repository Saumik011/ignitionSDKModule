/**
 * PopupProvider — Optional invisible palette component.
 *
 * The popup system is ZERO-CONFIG and works without this component.
 * PopupManager is initialized automatically from rad-client-components.ts
 * and polls the gateway for pending popups via HTTP — no component placement needed.
 *
 * This component remains in the palette for:
 *  - Backward compatibility with projects that already placed it
 *  - Providing a visual indicator in the designer that the popup module is active
 *
 * It renders nothing visible and requires no configuration.
 */

import * as React from 'react';
import {
    AbstractUIElementStore,
    Component,
    ComponentMeta,
    ComponentProps,
    ComponentStoreDelegate,
    PropertyTree,
    SizeObject,
    PComponent
} from '@inductiveautomation/perspective-client';

export const COMPONENT_TYPE = "rad.display.popupprovider";

/**
 * No-op delegate. The popup system uses HTTP polling (PopupManager.ts),
 * not websocket events — so this delegate does nothing.
 * It exists only to satisfy the ComponentMeta contract.
 */
export class PopupProviderDelegate extends ComponentStoreDelegate {
    constructor(componentStore: AbstractUIElementStore) {
        super(componentStore);
    }

    handleEvent(_eventName: string, _eventObject: object): void {
        // No-op: popup delivery is handled by the polling loop in PopupManager.ts
    }
}

export class PopupProvider extends Component<ComponentProps<Record<string, never>>> {

    render(): React.ReactNode {
        const { emit } = this.props;
        // Invisible placeholder — renders a zero-size hidden div
        return (
            <div
                {...emit({ classes: ['rad-popup-provider-component'] })}
                style={{ display: 'none', width: 0, height: 0, overflow: 'hidden', position: 'absolute' }}
            />
        );
    }
}

export class PopupProviderMeta implements ComponentMeta {
    getComponentType(): string {
        return COMPONENT_TYPE;
    }

    getDefaultSize(): SizeObject {
        return { width: 0, height: 0 };
    }

    getPropsReducer(_tree: PropertyTree): Record<string, never> {
        return {};
    }

    createDelegate(component: AbstractUIElementStore): ComponentStoreDelegate | undefined {
        return new PopupProviderDelegate(component);
    }

    getViewComponent(): PComponent {
        return PopupProvider;
    }
}
