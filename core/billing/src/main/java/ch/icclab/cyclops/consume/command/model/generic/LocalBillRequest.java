package ch.icclab.cyclops.consume.command.model.generic;
/*
 * Copyright (c) 2016. Zuercher Hochschule fuer Angewandte Wissenschaften
 *  All Rights Reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may
 *     not use this file except in compliance with the License. You may obtain
 *     a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *     WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *     License for the specific language governing permissions and limitations
 *     under the License.
 */

import ch.icclab.cyclops.consume.command.Command;
import ch.icclab.cyclops.consume.command.model.generic.local.Bill;
import ch.icclab.cyclops.consume.command.model.generic.local.CDR;
import ch.icclab.cyclops.load.Loader;
import ch.icclab.cyclops.publish.APICaller;
import ch.icclab.cyclops.timeseries.GenerateDBPoint;
import ch.icclab.cyclops.timeseries.InfluxDBClient;
import ch.icclab.cyclops.util.loggers.CommandLogger;
import java.net.URL;
import java.util.List;

/**
 * Author: Skoviera
 * Created: 14/09/16
 * Description: Local bill request that doesn't call Rule engine
 */
public class LocalBillRequest extends Command {

    private class URLs {
        String CDRMicroServiceURL;

        public URLs() {
        }

        public boolean isValid() {
            return CDRMicroServiceURL != null && !CDRMicroServiceURL.isEmpty();
        }
    }

    // account holder for the bill
    private String account;

    // linked accounts in federated billing
    private List<String> linked;

    // time boundaries for the bill
    private Long from;
    private Long to;

    // storage preference
    private boolean persist;

    @Override
    protected Object execute() {
        try {
            // sanity checks first
            if (from == null || to == null || from < 0l || to <= from)
                return "[ERROR] invalid FROM and TO";
            if (account == null || account.isEmpty())
                return "[ERROR] invalid account name";

            CommandLogger.log("Received Local Bill Request, thus asking CDR for data and generating a bill locally");

            // load URLS from configuration file
            URLs urls = Loader.extractProperties(URLs.class);

            if (urls != null && urls.isValid()) {
                // prepare flush command
                FlushData flush = new FlushData(from, to, account, linked);
                flush.disableSync();
                flush.enableOutput();

                // send flush data request
                APICaller.Response response = new APICaller().post(new URL(String.format("http://%s/command", urls.CDRMicroServiceURL)), flush);

                // parse as list of CDRs
                List<CDR> cdrs = response.getAsListOfType(CDR.class);

                // create a bill
                Bill bill = new Bill(account, from, to);

                // process received CDR records
                bill.addAndProcessCDRs(cdrs);

                // shall we persist it?
                if (persist) {
                    new InfluxDBClient().persistSinglePoint(GenerateDBPoint.fromObjectWithTimeAndTags(bill, Bill.getTimeFieldName(), Bill.getTimeUnit(), Bill.getTagNames()));
                }

                return bill;
            } else {
                return "Check configuration file, command endpoints are not valid";
            }

        } catch (Exception e) {
            return String.format("No data [%s]", e.getMessage());
        }
    }
}
