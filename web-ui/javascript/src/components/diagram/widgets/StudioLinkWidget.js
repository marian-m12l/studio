/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {DefaultLinkPointWidget, DiagramEngine, LinkWidget, PointModel} from '@projectstorm/react-diagrams';

import StudioLinkModel from "../models/StudioLinkModel";
import StudioLinkSegmentWidget from "./StudioLinkSegmentWidget";


class StudioLinkWidget extends React.Component {

    constructor(props) {
        super(props);
        this.refPaths = [];
        this.state = {
            selected: false
        };
    }

    componentDidUpdate() {
        this.props.link.setRenderedPaths(
            this.refPaths.map(ref => {
                return ref.current;
            })
        );
    }

    componentDidMount() {
        this.props.link.setRenderedPaths(
            this.refPaths.map(ref => {
                return ref.current;
            })
        );
    }

    componentWillUnmount() {
        this.props.link.setRenderedPaths([]);
    }

    addPointToLink(event, index) {
        // Ctrl key must be pressed to add a point
        if (
            event.ctrlKey &&
            !event.shiftKey &&
            !this.props.link.isLocked() &&
            this.props.link.getPoints().length - 1 <= this.props.diagramEngine.getMaxNumberPointsPerLink()
        ) {
            const point = new PointModel({
                link: this.props.link,
                position: this.props.diagramEngine.getRelativeMousePoint(event)
            });
            this.props.link.addPoint(point, index);
            event.persist();
            event.stopPropagation();
            this.forceUpdate(() => {
                this.props.diagramEngine.getActionEventBus().fireAction({
                    event,
                    model: point
                });
            });
        }
    }

    generatePoint(point) {
        return (
            <DefaultLinkPointWidget
                key={point.getID()}
                point={point}
                colorSelected={this.props.link.getOptions().selectedColor}
                color={this.props.link.getOptions().color}
            />
        );
    }

    generateLink(path, extraProps, id, firstSegment, lastSegment) {
        const ref = React.createRef();
        this.refPaths.push(ref);
        return (
            <StudioLinkSegmentWidget
                key={`link-${id}`}
                path={path}
                selected={this.state.selected}
                diagramEngine={this.props.diagramEngine}
                factory={this.props.diagramEngine.getFactoryForLink(this.props.link)}
                link={this.props.link}
                forwardRef={ref}
                onSelection={selected => {
                    this.setState({ selected: selected });
                }}
                extras={extraProps}
                firstSegment={firstSegment}
                lastSegment={lastSegment}
            />
        );
    }

    render() {
        //ensure id is present for all points on the path
        var points = this.props.link.getPoints();
        var paths = [];
        this.refPaths = [];

        if (points.length === 2) {
            paths.push(
                this.generateLink(
                    this.props.link.getSVGPath(),
                    {
                        onMouseDown: event => {
                            this.addPointToLink(event, 1);
                        }
                    },
                    '0',
                    true,
                    true
                )
            );
        } else {
            //draw the multiple anchors and complex line instead
            for (let j = 0; j < points.length - 1; j++) {
                paths.push(
                    this.generateLink(
                        LinkWidget.generateLinePath(points[j], points[j + 1]),
                        {
                            'data-linkid': this.props.link.getID(),
                            'data-point': j,
                            onMouseDown: (event) => {
                                this.addPointToLink(event, j + 1);
                            }
                        },
                        j,
                        (j === 0),
                        (j === points.length - 2)
                    )
                );
            }

            //render the circles
            for (let i = 1; i < points.length - 1; i++) {
                paths.push(this.generatePoint(points[i]));
            }
        }

        const isReturnWorkflow = this.props.link.getForwardSourcePort() && this.props.link.getForwardSourcePort().isHome;

        return <g className={`studio-link ${isReturnWorkflow ? 'returning-workflow' : ''}`} data-default-link-test={this.props.link.getOptions().testName}>{paths}</g>;
    }

}

StudioLinkWidget.propTypes = {
    link: PropTypes.instanceOf(StudioLinkModel).isRequired,
    diagramEngine: PropTypes.instanceOf(DiagramEngine).isRequired
};

export default StudioLinkWidget
