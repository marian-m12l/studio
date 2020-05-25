/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import {Action, InputType} from '@projectstorm/react-canvas-core';


// Corrects the default ZoomCanvasAction to fix slow wheel/zoom in firefox
class FixedZoomCanvasAction extends Action {

    constructor() {
        super({
            type: InputType.MOUSE_WHEEL,
            fire: (actionEvent) => {
                const {event} = actionEvent;
                // we can block layer rendering because we are only targeting the transforms
                for (let layer of this.engine.getModel().getLayers()) {
                    layer.allowRepaint(false);
                }

                const model = this.engine.getModel();
                event.stopPropagation();
                const oldZoomFactor = this.engine.getModel().getZoomLevel() / 100;
                // inverse zoom
                let scrollDelta = -event.deltaY;
                // fix firefox slowness
                if (event.deltaMode === WheelEvent.DOM_DELTA_LINE) {
                    console.log('Fixing wheel event');
                    console.log('Delta line Y = ' + event.deltaY);
                    console.log('Delta pixel Y = ' + (20 * event.deltaY));
                    event.stopPropagation();
                    const customScroll = new WheelEvent('wheel', {
                        bubbles: event.bubbles,
                        deltaMode: WheelEvent.DOM_DELTA_PIXEL,
                        clientX: event.clientX,
                        clientY: event.clientY,
                        deltaX: event.deltaX,
                        deltaY: 20 * event.deltaY,
                    });
                    event.target.dispatchEvent(customScroll);
                }
                //check if it is pinch gesture
                if (event.ctrlKey && scrollDelta % 1 !== 0) {
                    /*
                        Chrome and Firefox sends wheel event with deltaY that
                        have fractional part, also `ctrlKey` prop of the event is true
                        though ctrl isn't pressed
                    */
                    scrollDelta /= 3;
                } else {
                    scrollDelta /= 60;
                }
                if (model.getZoomLevel() + scrollDelta > 10) {
                    model.setZoomLevel(model.getZoomLevel() + scrollDelta);
                }

                const zoomFactor = model.getZoomLevel() / 100;

                const boundingRect = event.currentTarget.getBoundingClientRect();
                const clientWidth = boundingRect.width;
                const clientHeight = boundingRect.height;
                // compute difference between rect before and after scroll
                const widthDiff = clientWidth * zoomFactor - clientWidth * oldZoomFactor;
                const heightDiff = clientHeight * zoomFactor - clientHeight * oldZoomFactor;
                // compute mouse coords relative to canvas
                const clientX = event.clientX - boundingRect.left;
                const clientY = event.clientY - boundingRect.top;

                // compute width and height increment factor
                const xFactor = (clientX - model.getOffsetX()) / oldZoomFactor / clientWidth;
                const yFactor = (clientY - model.getOffsetY()) / oldZoomFactor / clientHeight;

                model.setOffset(model.getOffsetX() - widthDiff * xFactor, model.getOffsetY() - heightDiff * yFactor);
                this.engine.repaintCanvas();

                // re-enable rendering
                for (let layer of this.engine.getModel().getLayers()) {
                    layer.allowRepaint(true);
                }
            }
        });
    }

}

export default FixedZoomCanvasAction;
