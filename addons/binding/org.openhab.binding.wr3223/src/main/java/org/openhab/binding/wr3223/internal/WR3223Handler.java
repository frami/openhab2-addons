/**
 * Copyright (c) 2014,2019 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.wr3223.internal;

import static org.openhab.binding.wr3223.internal.WR3223BindingConstants.CHANNEL_ADDITIONAL_HEATER_ACTIVATE;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.openhab.binding.wr3223.internal.controller.WR3223CommandType;
import org.openhab.binding.wr3223.internal.controller.WR3223Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WR3223Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Michael Fraefel - Initial contribution
 */
@NonNullByDefault
public class WR3223Handler extends BaseThingHandler implements WR3223Controller.Callback {

    final Logger logger = LoggerFactory.getLogger(WR3223Handler.class);

    @Nullable
    private ScheduledFuture<?> pollingJob;

    @Nullable
    private WR3223Configuration config;

    private final SerialPortManager serialPortManager;

    public WR3223Handler(Thing thing, SerialPortManager serialPortManager) {
        super(thing);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_ADDITIONAL_HEATER_ACTIVATE.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
            }

            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information:
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    @Override
    public void initialize() {
        // logger.debug("Start initializing!");
        config = getConfigAs(WR3223Configuration.class);

        updateStatus(ThingStatus.UNKNOWN);

        Runnable runnable = new WR3223Controller(config, serialPortManager, getThing().getUID().getAsString(), this);
        pollingJob = scheduler.scheduleWithFixedDelay(runnable, 0L, config.refreshInterval, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        pollingJob.cancel(true);
    }

    /**
     * Publish the value to all bound items.
     *
     * @param wr3223CommandType
     * @param value
     */
    @Override
    public void publishValueToBoundChannel(WR3223CommandType wr3223CommandType, @Nullable Object value) {
        if (value == null) {
            logger.error("Can't set NULL value to channel id {}.", wr3223CommandType.getChannelId());
            return;
        }
        Channel channel = getThing().getChannel(wr3223CommandType.getChannelId());
        if (channel == null) {
            logger.error("No channel with id {} for command {}.", wr3223CommandType.getChannelId(),
                    wr3223CommandType.name());
            return;
        }
        if (!isLinked(channel.getUID())) {
            return;
        }
        String type = channel.getAcceptedItemType();
        if (type == null) {
            logger.error("No type for channel with id {} set.", wr3223CommandType.getChannelId());
            return;
        }
        if (type.contains(":")) {
            type = type.substring(0, type.indexOf(":"));
        }

        State state = null;
        if (type.equals("Number")) {
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Publish command {} with number {} to channel {}", wr3223CommandType.name(),
                            value.toString().trim(), wr3223CommandType.getChannelId());
                }
                state = DecimalType.valueOf(value.toString().trim());
            } catch (NumberFormatException nfe) {
                logger.error("Can't set value {} to channel type {} because it's not a decimal number.", value, type);
            }
        } else if (type.equals("Switch")) {
            state = parseBooleanValue(value);
            if (logger.isDebugEnabled()) {
                logger.debug("Publish command {} with boolean {} to channel {}", wr3223CommandType.name(), state,
                        wr3223CommandType.getChannelId());
            }
        } else if (type.equals("Contact")) {
            state = parseBooleanValue(value) == OnOffType.ON ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            if (logger.isDebugEnabled()) {
                logger.debug("Publish command {} with boolean {} to channel {}", wr3223CommandType.name(), state);
            }
        }
        if (state != null) {
            updateState(channel.getUID(), state);
        } else {
            logger.error("Can't set value {} of command {} to channel {}.", value, wr3223CommandType.name(),
                    wr3223CommandType.getChannelId());
        }
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        super.updateStatus(status, statusDetail, description);
    }

    @Override
    public boolean isLinked(@NonNull WR3223CommandType wr3223CommandType) {
        return isLinked(wr3223CommandType.getChannelId());
    }

    /**
     * Try to read the On/Off state.
     *
     * @param value
     * @return state of a boolean value
     */
    private State parseBooleanValue(Object value) {
        State state;
        String valStr = value.toString().trim();
        state = (valStr.equalsIgnoreCase("true") || valStr.equals("1") || valStr.equals("1.")) ? OnOffType.ON
                : OnOffType.OFF;
        if (logger.isDebugEnabled()) {
            logger.debug("Parsed value {} to state {}", valStr, state);
        }
        return state;
    }

}
