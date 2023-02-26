/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TransferStatus {

    private long transferred;
    private long total;
    private double speed;

    /** Check if transfer is complete. */
    public boolean isDone() {
        return transferred == total;
    }

    /** Transfer percent. */
    public double getPercent() {
        return transferred / (double) total;
    }
}
