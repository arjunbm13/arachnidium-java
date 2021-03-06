package com.github.arachnidium.core;

import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.github.arachnidium.util.logging.Log;
import com.github.arachnidium.util.logging.Photographer;
import com.github.arachnidium.util.proxy.EnhancedProxyFactory;

import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.springframework.context.annotation.Bean;

import com.github.arachnidium.core.components.common.AlertHandler;
import com.github.arachnidium.core.components.common.Awaiting;
import com.github.arachnidium.core.fluenthandle.IHowToGetHandle;
import com.github.arachnidium.core.interfaces.IDestroyable;
import com.github.arachnidium.core.interfaces.IHasHandle;
import com.github.arachnidium.core.settings.AlertIsPresentTimeOut;
import com.github.arachnidium.core.settings.HandleWaitingTimeOut;

/**
 * This an abstraction that describes the
 * way how to get a new {@link Handle} and 
 * how to switch from one another
 * 
 * @param <U> it is a s strategy of the {@link Handle} receiving
 * @param <V> it is the expected {@link Handle} class e.g {@link BrowserWindow} or {@link MobileScreen}
 */
public abstract class Manager<U extends IHowToGetHandle, V extends Handle> implements IDestroyable {

	static long getTimeOut(Long possibleTimeOut) {
		if (possibleTimeOut == null)
			return defaultTimeOut;
		else
			return possibleTimeOut;
	}

	final Awaiting awaiting;
	private final WebDriverEncapsulation driverEncapsulation;
	boolean isAlive = true;
	private final HandleReceptionist handleReceptionist = new HandleReceptionist();

	private final static Map<WebDriverEncapsulation, Manager<?,?>> managerMap = Collections
			.synchronizedMap(new HashMap<WebDriverEncapsulation, Manager<?,?>>());
	final static long defaultTimeOut = 5; // we will wait
	private String STUB_HANDLE = "STUB";
	
	/**
	 * @param driverEncapsulation
	 *            Instantiated {@link WebDriverEncapsulation}
	 * @return If there is an instantiated {@link Manager} binded with the given
	 *         {@link WebDriverEncapsulation} this method returns the instance.
	 *         In another case it returns <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Manager<?,?>> T getInstanstiatedManager(
			WebDriverEncapsulation driverEncapsulation) {
		return (T) managerMap.get(driverEncapsulation);
	}

	Manager(WebDriverEncapsulation initialDriverEncapsulation) {
		driverEncapsulation = initialDriverEncapsulation;
		awaiting = new Awaiting(driverEncapsulation.getWrappedDriver());
		managerMap.put(driverEncapsulation, this);
		driverEncapsulation.addDestroyable(this);
	}

	/**
	 * Focus on the given window or mobile context will be implemented by
	 * subclasses
	 */
	abstract void changeActive(String handle);

	/**
	 * This method destroys information
	 * about related windows or mobile contexts
	 */
	@Override
	public void destroy() {
		managerMap.remove(driverEncapsulation);
		isAlive = false;
		List<IHasHandle> toBeDestroyed = handleReceptionist.getInstantiated();
		toBeDestroyed.forEach((hasHandle) -> ((IDestroyable) hasHandle)
				.destroy());
	}

	/**
	 * @return {@link Alert} which is present
	 * @throws {@link NoAlertPresentException}
	 */
	public Alert getAlert() throws NoAlertPresentException{
		Long time = driverEncapsulation.getWrappedConfiguration()
				.getSection(AlertIsPresentTimeOut.class).getAlertIsPresentTimeOut();
		return driverEncapsulation.getComponent(AlertHandler.class,
				new Class[] { long.class },
				new Object[] {time});
		
	}

	/**
	 * @param timeOut It is an explicitly given time (seconds) 
	 *    to wait for Alert is present
	 * @return {@link Alert} which is present
	 * @throws {@link NoAlertPresentException}
	 */
	public synchronized Alert getAlert(long timeOut)
			throws NoAlertPresentException {
		return driverEncapsulation.getComponent(AlertHandler.class,
				new Class[] { long.class },
				new Object[] { timeOut });
	}

	/**
	 * @param An expected window/mobile context index
	 * @return Window or mobile context. Actually it returns CGLIB proxy
	 * which instantiate the real object by the invocation 
	 */
	public V getHandle(int index){
		return getHandle(index, getTimeOut(getHandleWaitingTimeOut()
				.getHandleWaitingTimeOut())); 
	}
	
	/**
	 * @param An expected window/mobile context index
	 * @param It is an explicitly given time (seconds) to wait for
	 *            window/mobile context is present
	 * @return Window or mobile context. Actually it returns CGLIB proxy
	 * which instantiate the real object by the invocation 
	 */
	@SuppressWarnings("unchecked")
	public V getHandle(int index, long timeOut){
		ParameterizedType generic = (ParameterizedType) this.getClass().getGenericSuperclass();
		Class<U> howToGetClass = null;
		try {
			howToGetClass = (Class<U>) Class
					.forName(generic.getActualTypeArguments()[0].getTypeName());
		} catch (Exception e) {
			throw new RuntimeException(e); 
		}
		
		U howToGet = null;
		try {
			howToGet = howToGetClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		howToGet.setExpected(index);
		return getHandle(timeOut, howToGet);
	}
	
	HandleReceptionist getHandleReceptionist() {
		return handleReceptionist;
	}

	/**
	 * @return Set of string window handles/context names
	 */
	abstract Set<String> getHandles();

	/**
	 * Returns window on mobile context 
	 * by conditions. 
	 * 
	 * @param howToGet Given strategy. 
	 * @return Window or mobile context. Actually it returns CGLIB proxy
	 * which instantiate the real object by the invocation 
	 * 
	 * @see IHowToGetHandle
	 */
	public V getHandle(U howToGet){
		return getHandle(getTimeOut(getHandleWaitingTimeOut()
				.getHandleWaitingTimeOut()), howToGet); 
	}
	
	/**
	 * Returns window on mobile context 
	 * by conditions. 
	 * 
	 * @param timeOut It is an explicitly given time (seconds) to wait for
	 *            window/mobile context is present
	 *            
	 * @param howToGet Given strategy.
	 * @return Window or mobile context. Actually it returns CGLIB proxy
	 * which instantiate the real object by the invocation 
	 * 
	 * @see IHowToGetHandle. 
	 */
	@SuppressWarnings("unchecked")
	public V getHandle(long timeOut, U howToGet){
		HandleInterceptor<U> hi = new HandleInterceptor<U>(
				this, howToGet, timeOut);
		Class<?>[] params = new Class<?>[] {String.class, this.getClass()};
		Object[] values = new Object[] {STUB_HANDLE, this};
		ParameterizedType generic = (ParameterizedType) this.getClass().getGenericSuperclass();
		Class<V> required = null;
		try {
			required = (Class<V>) Class
					.forName(generic.getActualTypeArguments()[1].getTypeName());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return EnhancedProxyFactory.getProxy(required, params, values, hi);
	}
	
	/**
	 * Returns window on mobile context 
	 * by conditions. 
	 * 
	 * @param timeOut It is an explicitly given time (seconds) to wait for
	 *            window/mobile context is present
	 *            
	 * @param howToGet Given strategy.
	 * @return Window or mobile context.
	 *  
	 * @see IHowToGetHandle. 
	 */
	abstract V getRealHandle(long timeOut, U howToGet);	

	WebDriverEncapsulation getWebDriverEncapsulation() {
		return driverEncapsulation;
	}

	WebDriver getWrappedDriver() {
		return driverEncapsulation.getWrappedDriver();
	}

	/**
	 * @return <code>false</code> if wrapped {@link WebDriver} was shut down
	 */
	boolean isAlive() {
		return isAlive;
	}

	/**
	 * Sets focus on window/mobile context by string 
	 * parameter
	 * 
	 * @param String window handle/context name
	 */
	synchronized void switchTo(String Handle) {
		changeActive(Handle);
	}

	/**
	 * @param timeOut It is an explicitly given time (seconds) to wait for
	 *            window/mobile context is present
	 *            
	 * @param howToGet Given strategy. 
	 * @return Window handle/context name
	 * 
	 * @see IHowToGetHandle
	 */
	abstract String getStringHandle(long timeOut, U howToGet);

	/**
	 * Takes a picture of the given window/mobile context.
	 * It creates FINE {@link Level} {@link Log} message with 
	 * attached picture 
	 * 
	 * @param handle String window handle/context name
	 * @param comment Narrative message text
	 */
	synchronized void takeAPictureOfAFine(String handle, String comment) {
		changeActive(handle);
		Photographer.takeAPictureOfAFine(
				driverEncapsulation.getWrappedDriver(), comment);
	}

	/**
	 * Takes a picture of the given window/mobile context.
	 * It creates INFO {@link Level} {@link Log} message with 
	 * attached picture 
	 * 
	 * @param handle String window handle/context name
	 * @param comment Narrative message text
	 */	
	synchronized void takeAPictureOfAnInfo(String handle, String comment) {
		changeActive(handle);
		Photographer.takeAPictureOfAnInfo(
				driverEncapsulation.getWrappedDriver(), comment);
	}
	
	/**
	 * Takes a picture of the given window/mobile context.
	 * It creates SEVERE {@link Level} {@link Log} message with 
	 * attached picture 
	 * 
	 * @param handle String window handle/context name
	 * @param comment Narrative message text
	 */		
	synchronized void takeAPictureOfASevere(String handle, String comment) {
		changeActive(handle);
		Photographer.takeAPictureOfASevere(
				driverEncapsulation.getWrappedDriver(), comment);
	}

	/**
	 * Takes a picture of the given window/mobile context.
	 * It creates WARN {@link Level} {@link Log} message with 
	 * attached picture 
	 * 
	 * @param handle String window handle/context name
	 * @param comment Narrative message text
	 */		
	synchronized void takeAPictureOfAWarning(String handle, String comment) {
		changeActive(handle);
		Photographer.takeAPictureOfAWarning(
				driverEncapsulation.getWrappedDriver(), comment);
	}
	
	/**
	 * Gets a new created listenable {@link Handle} and notifies listener
	 * that there it is a new object
	 * 
	 * @param handle instantiated {@link Handle}
	 * @param beanName Is a name of the {@link Bean}
	 * @return listenable {@link Handle} instance
	 */
	@SuppressWarnings("unchecked")
	<T extends Handle> T returnNewCreatedListenableHandle(Handle handle, String beanName){
		T result = (T) driverEncapsulation.context.getBean(beanName, handle);
		result.whenIsCreated();
		getHandleReceptionist().addKnown(result);
		return result;
	}
	
	HandleWaitingTimeOut getHandleWaitingTimeOut() {
		return driverEncapsulation.getWrappedConfiguration()
				.getSection(HandleWaitingTimeOut.class);
	}
}
