import { InjectionToken } from '@angular/core';

// Key used by Angular DI to inject "data" into the component shown inside the modal.
// We use an InjectionToken because the data is just a plain object (not a class/service).
export const MODAL_DATA = new InjectionToken<any>('MODAL_DATA');
