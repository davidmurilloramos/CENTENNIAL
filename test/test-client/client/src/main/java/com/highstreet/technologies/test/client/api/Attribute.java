/*
 * Copyright (c) 2016 highstreet technologies GmbH and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.highstreet.technologies.test.client.api;

import com.highstreet.technologies.test.client.enums.ConditionalPackage;
import com.highstreet.technologies.test.client.enums.SubObjectClass;

public interface Attribute {
	
    public ConditionalPackage getConditionalPackage();
    
    public String getLayerProtocol();
    
	public SubObjectClass getSubObjectClass();

	public String getAttribute();
	
	public String toJsonString();

}