/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.inikah.slayer.service.impl;

import java.util.Date;
import java.util.List;

import com.inikah.slayer.model.Location;
import com.inikah.slayer.model.Profile;
import com.inikah.slayer.service.BridgeServiceUtil;
import com.inikah.slayer.service.base.ProfileLocalServiceBaseImpl;
import com.inikah.util.IConstants;
import com.inikah.util.MyListUtil;
import com.inikah.util.ProfileCodeUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Address;
import com.liferay.portal.model.Phone;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.CompanyThreadLocal;
import com.liferay.portal.service.ServiceContext;

/**
 * The implementation of the profile local service.
 *
 * <p>
 * All custom service methods should be put in this class. Whenever methods are added, rerun ServiceBuilder to copy their definitions into the {@link com.slayer.service.ProfileLocalService} interface.
 *
 * <p>
 * This is a local service. Methods of this service will not have security checks based on the propagated JAAS credentials because this service can only be accessed from within the same VM.
 * </p>
 *
 * @author Ahmed Hasan
 * @see com.slayer.service.base.ProfileLocalServiceBaseImpl
 * @see com.slayer.service.ProfileLocalServiceUtil
 */
public class ProfileLocalServiceImpl extends ProfileLocalServiceBaseImpl {
	/*
	 * NOTE FOR DEVELOPERS:
	 *
	 * Never reference this interface directly. Always use {@link com.slayer.service.ProfileLocalServiceUtil} to access the profile local service.
	 */
	
	/**
	 * 
	 */
	public Profile init(boolean bride, String emailAddress, String profileName, boolean createdForSelf, ServiceContext serviceContext) {

		Profile profile = createProfile(bride);
				
		// setting values from serviceContext
		profile.setCompanyId(serviceContext.getCompanyId());
		profile.setGroupId(serviceContext.getScopeGroupId());
		profile.setUserId(serviceContext.getUserId());
		profile.setCreateDate(new Date());
		profile.setOwnerLastLogin(new Date());
				
		// basic info
		profile.setBride(bride);
		profile.setProfileName(profileName);
		profile.setEmailAddress(emailAddress);
		profile.setEmailAddressVerified(true);
		
		if (createdForSelf) {
			profile.setCreatedFor(BridgeServiceUtil.getListTypeId(IConstants.LIST_CREATED_FOR, "self"));
		}
				
		try {
			profile = addProfile(profile);
		} catch (SystemException e) {
			e.printStackTrace();
		}
		
		return profile;		
	}
	
	/**
	 * 
	 * @param bride
	 * @return
	 */
	private Profile createProfile(boolean bride) {
		long profileId = 0l;
		try {
			profileId = counterLocalService.increment(Profile.class.getName());
		} catch (SystemException e) {
			e.printStackTrace();
		}		
		
		Profile profile = super.createProfile(profileId);
		profile.setProfileCode(ProfileCodeUtil.getCode(bride, profileId));
		
		return profile;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.inikah.slayer.service.ProfileLocalService#updateStatus(long, java.lang.String)
	 */
	public void updateStatus(long profileId, String status) {
		
		Profile profile = null;
		try {
			profile = fetchProfile(profileId);
		} catch (SystemException e) {
			e.printStackTrace();
		}
		
		if (Validator.isNull(profile)) return;
		
		//profile.setStatus(status);
		try {
			updateProfile(profile);
		} catch (SystemException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 */
	public void setOwnerLastLogin(long userId) {
		try {
			List<Profile> profiles = profilePersistence.findByUserId(userId);
			
			for (Profile profile: profiles) {
				profile.setOwnerLastLogin(new Date());
				updateProfile(profile);
			}
		} catch (SystemException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param emailAddress
	 * @return
	 */
	private Profile getProfileByEmail(String emailAddress) {
		
		long companyId = CompanyThreadLocal.getCompanyId();
		
		long userId = 0l;
		try {
			userId = userLocalService.getDefaultUserId(companyId);
		} catch (PortalException e) {
			e.printStackTrace();
		} catch (SystemException e) {
			e.printStackTrace();
		}
				
		Profile profile = null;
		try {
			profile = profilePersistence.fetchByUserId_EmailAddress(userId, emailAddress);
		} catch (SystemException e) {
			e.printStackTrace();
		}

		return profile;
	}
	
	/**
	 * 
	 * @param user
	 * @param serviceContext
	 */
	public void attachProfileToUser(User user) {

		Profile profile = getProfileByEmail(user.getEmailAddress());
		
		if (Validator.isNull(profile)) return; 
		
		profile.setUserId(user.getUserId());
		profile.setUserName(user.getFirstName());
		
		profile.setDefaultLocation(user);
		
		try {
			updateProfile(profile);
		} catch (SystemException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 */
	public boolean hasSelfProfile(long userId) {
		
		boolean flag = false;
		
		int createdFor = MyListUtil.getListTypeId(IConstants.LIST_CREATED_FOR, "self");
		
		try {
			Profile profile = profilePersistence.fetchByUserId_CreatedFor(userId, createdFor);
			
			flag = (Validator.isNotNull(profile));
		} catch (SystemException e) {
			e.printStackTrace();
		}
		
		return flag;
	}
	
	/**
	 * 
	 * @param userId
	 * @return
	 */
	public List<Profile> getProfilesForUser(long userId) {
		List<Profile> profiles = null;
		try {
			profiles = profilePersistence.findByUserId(userId);
		} catch (SystemException e) {
			e.printStackTrace();
		}
		return profiles;
	}
	
	/**
	 * 
	 */
	public Address getMaxMindAddress(User user) {
		
		Address address = null;
		
		try {
			List<Address> addresses = 
					addressLocalService.getAddresses(
							user.getCompanyId(), Location.class.getName(), user.getUserId());
			
			if (Validator.isNotNull(addresses) && !addresses.isEmpty()) {
				address = addresses.get(0);
			}
		} catch (SystemException e) {
			e.printStackTrace();
		}
		
		return address; 
	}
	
	/**
	 * 
	 */
	public boolean maxMindCoordinatesSet(User user) {		
		
		return Validator.isNotNull(getMaxMindAddress(user));
	}
	
	public Phone addPhone(long classPK, String idd, String number, ServiceContext serviceContext, boolean primary) {
		
		Phone phone = null;
		
		try {
			phone = phoneLocalService.addPhone(serviceContext.getUserId(), Profile.class.getName(), classPK, number, idd, 0, primary, serviceContext);
		} catch (PortalException e) {
			e.printStackTrace();
		} catch (SystemException e) {
			e.printStackTrace();
		}
		
		return phone;
	}
}