/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.files;

import net.sourceforge.cobertura.coveragedata.ProjectData;

import org.osgi.framework.BundleContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoberturaActivator extends Activator {
  private static final Logger LOGGER = LoggerFactory.getLogger(CoberturaActivator.class);
  
  @Override
  public void stop(BundleContext bundleContext) throws Exception {
    super.stop(bundleContext);
    try {
      LOGGER.info("stopping bundle, writing code coverage data");
      ProjectData.saveGlobalProjectData();
    } catch (Exception e) {
      LOGGER.warn("problem writing code coverage data during bundle stop");
    }
  }
}
           