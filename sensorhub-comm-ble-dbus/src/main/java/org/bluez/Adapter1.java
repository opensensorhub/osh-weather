/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.bluez;

import java.util.Map;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;


/**
 * <p>
 * BlueZ 5 D-Bus Adapter API
 * </p>

Adapter hierarchy
=================

Service     org.bluez
Interface   org.bluez.Adapter1
Object path [variable prefix]/{hci0,hci1,...}

Methods     void StartDiscovery()

            This method starts the device discovery session. This
            includes an inquiry procedure and remote device name
            resolving. Use StopDiscovery to release the sessions
            acquired.

            This process will start creating Device objects as
            new devices are discovered.

            During discovery RSSI delta-threshold is imposed.

            Possible errors: org.bluez.Error.NotReady
                     org.bluez.Error.Failed

        void StopDiscovery()

            This method will cancel any previous StartDiscovery
            transaction.

            Note that a discovery procedure is shared between all
            discovery sessions thus calling StopDiscovery will only
            release a single session.

            Possible errors: org.bluez.Error.NotReady
                     org.bluez.Error.Failed
                     org.bluez.Error.NotAuthorized

        void RemoveDevice(object device)

            This removes the remote device object at the given
            path. It will remove also the pairing information.

            Possible errors: org.bluez.Error.InvalidArguments
                     org.bluez.Error.Failed

        void SetDiscoveryFilter(dict filter) [Experimental]

            This method sets the device discovery filter for the
            caller. When this method is called with no filter
            parameter, filter is removed.

            Parameters that may be set in the filter dictionary
            include the following:

            array{string} UUIDs : filtered service UUIDs
            int16         RSSI  : RSSI threshold value
            uint16        Pathloss  : Pathloss threshold value
            string        Transport : type of scan to run

            When a remote device is found that advertises any UUID
            from UUIDs, it will be reported if:
            - Pathloss and RSSI are both empty,
            - only Pathloss param is set, device advertise TX pwer,
              and computed pathloss is less than Pathloss param,
            - only RSSI param is set, and received RSSI is higher
              than RSSI param,

            Transport parameter determines the type of scan:
                "auto"  - interleaved scan, default value
                "bredr" - BR/EDR inquiry
                "le"    - LE scan only

            If "le" or "bredr" Transport is requested, and the
            controller doesn't support it, org.bluez.Error.Failed
            error will be returned. If "auto" transport is
            requested, scan will use LE, BREDR, or both, depending
            on what's currently enabled on the controller.

            When discovery filter is set, Device objects will be
            created as new devices with matching criteria are
            discovered. PropertiesChanged signals will be emitted
            for already existing Device objects, with updated RSSI
            value. If one or more discovery filters have been set,
            the RSSI delta-threshold, that is imposed by
            StartDiscovery by default, will not be applied.

            When multiple clients call SetDiscoveryFilter, their
            filters are internally merged, and notifications about
            new devices are sent to all clients. Therefore, each
            client must check that device updates actually match
            its filter.

            When SetDiscoveryFilter is called multiple times by the
            same client, last filter passed will be active for
            given client.

            SetDiscoveryFilter can be called before StartDiscovery.
            It is useful when client will create first discovery
            session, to ensure that proper scan will be started
            right after call to StartDiscovery.

            Possible errors: org.bluez.Error.NotReady
                     org.bluez.Error.Failed

Properties  string Address [readonly]

            The Bluetooth device address.

        string Name [readonly]

            The Bluetooth system name (pretty hostname).

            This property is either a static system default
            or controlled by an external daemon providing
            access to the pretty hostname configuration.

        string Alias [readwrite]

            The Bluetooth friendly name. This value can be
            changed.

            In case no alias is set, it will return the system
            provided name. Setting an empty string as alias will
            convert it back to the system provided name.

            When resetting the alias with an empty string, the
            property will default back to system name.

            On a well configured system, this property never
            needs to be changed since it defaults to the system
            name and provides the pretty hostname. Only if the
            local name needs to be different from the pretty
            hostname, this property should be used as last
            resort.

        uint32 Class [readonly]

            The Bluetooth class of device.

            This property represents the value that is either
            automatically configured by DMI/ACPI information
            or provided as static configuration.

        boolean Powered [readwrite]

            Switch an adapter on or off. This will also set the
            appropriate connectable state of the controller.

            The value of this property is not persistent. After
            restart or unplugging of the adapter it will reset
            back to false.

        boolean Discoverable [readwrite]

            Switch an adapter to discoverable or non-discoverable
            to either make it visible or hide it. This is a global
            setting and should only be used by the settings
            application.

            If the DiscoverableTimeout is set to a non-zero
            value then the system will set this value back to
            false after the timer expired.

            In case the adapter is switched off, setting this
            value will fail.

            When changing the Powered property the new state of
            this property will be updated via a PropertyChanged
            signal.

            For any new adapter this settings defaults to false.

        boolean Pairable [readwrite]

            Switch an adapter to pairable or non-pairable. This is
            a global setting and should only be used by the
            settings application.

            Note that this property only affects incoming pairing
            requests.

            For any new adapter this settings defaults to true.

        uint32 PairableTimeout [readwrite]

            The pairable timeout in seconds. A value of zero
            means that the timeout is disabled and it will stay in
            pairable mode forever.

            The default value for pairable timeout should be
            disabled (value 0).

        uint32 DiscoverableTimeout [readwrite]

            The discoverable timeout in seconds. A value of zero
            means that the timeout is disabled and it will stay in
            discoverable/limited mode forever.

            The default value for the discoverable timeout should
            be 180 seconds (3 minutes).

        boolean Discovering [readonly]

            Indicates that a device discovery procedure is active.

        array{string} UUIDs [readonly]

            List of 128-bit UUIDs that represents the available
            local services.

        string Modalias [readonly, optional]

            Local Device ID information in modalias format
            used by the kernel and udev.
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Feb 25, 2016
 */
public interface Adapter1 extends DBusInterface
{
    public void StartDiscovery();
    public void SetDiscoveryFilter(Map<String,Variant> properties);
    public void StopDiscovery();
    public void RemoveDevice(DBusInterface device);
}
