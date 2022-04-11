/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model;

public class TransferStatus {

    private long transferred;
    private long total;
    private double speed;

    public TransferStatus(long transferred, long total, double speed) {
        this.transferred = transferred;
        this.total = total;
        this.speed = speed;
    }

    /** Check if transfer is complete. */
    public boolean isDone() {
        return transferred == total;
    }

    /** Transfer percent. */
    public double getPercent() {
        return transferred / (double) total;
    }

    public long getTransferred() {
        return transferred;
    }

    public void setTransferred(long transferred) {
        this.transferred = transferred;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public String toString() {
        return "TransferStatus{" + ", transferred=" + transferred + ", total=" + total + ", speed=" + speed + '}';
    }
}
