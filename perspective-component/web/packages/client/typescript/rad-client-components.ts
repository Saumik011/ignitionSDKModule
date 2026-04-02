import {ComponentMeta, ComponentRegistry} from '@inductiveautomation/perspective-client';
import { Image, ImageMeta } from './components/Image';
import { MessengerComponent, MessengerComponentMeta } from './components/Messenger';
import { TagCounter, TagCounterMeta } from './components/TagCounter';
import { PopupProvider, PopupProviderMeta } from './components/PopupProvider';
import { PopupManager } from './popup/PopupManager';

// export so the components are referencable, e.g. `RadComponents['Image']
export {Image, MessengerComponent, TagCounter, PopupProvider};

import '../scss/main';

// as new components are implemented, import them, and add their meta to this array
const components: Array<ComponentMeta> = [
    new ImageMeta(),
    new MessengerComponentMeta(),
    new TagCounterMeta(),
    new PopupProviderMeta()
];

// iterate through our components, registering each one with the registry.  Don't forget to register on the Java side too!
components.forEach((c: ComponentMeta) => ComponentRegistry.register(c) );

// Initialize global popup notification system (session-level, zero-config)
PopupManager.initialize();

