/*
 * Copyright © 2016 highstreet technologies GmbH and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.highstreet.technologies.odl.app.impl;

import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.notifications.rev160809.AttributeValueChangedNotification;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.notifications.rev160809.MicrowaveModelNotificationsListener;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.notifications.rev160809.ObjectCreationNotification;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.notifications.rev160809.ObjectDeletionNotification;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.notifications.rev160809.ProblemNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ekis on 28.9.2016.
 * The class has not already implemented
 */
public class MicrowaveModelNotificationsHandler implements MicrowaveModelNotificationsListener {
    private static final Logger LOG = LoggerFactory.getLogger(MicrowaveModelNotificationsHandler.class);

    @Override
    public void onObjectCreationNotification(ObjectCreationNotification notification) {
        LOG.info("onObjectCreationNotification");
    }

    @Override
    public void onAttributeValueChangedNotification(AttributeValueChangedNotification notification) {
        LOG.info("onAttributeValueChangedNotification");
    }

    @Override
    public void onProblemNotification(ProblemNotification notification) {
        LOG.info("onProblemNotification");
    }

    @Override
    public void onObjectDeletionNotification(ObjectDeletionNotification notification) {
        LOG.info("onObjectDeletionNotification");
    }
}
