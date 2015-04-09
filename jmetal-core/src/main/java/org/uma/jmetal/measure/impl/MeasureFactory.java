package org.uma.jmetal.measure.impl;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.uma.jmetal.measure.Measure;
import org.uma.jmetal.measure.MeasureListener;
import org.uma.jmetal.measure.PullMeasure;
import org.uma.jmetal.measure.PushMeasure;

/**
 * The {@link MeasureFactory} provides some useful methods to build specific
 * {@link Measure}s.
 * 
 * @author Matthieu Vergne <matthieu.vergne@gmail.com>
 * 
 */
public class MeasureFactory {

	private final Logger log = Logger.getLogger(MeasureFactory.class.getName());

	/**
	 * Create a {@link PullMeasure} to backup the last {@link Value} of a
	 * {@link PushMeasure}. When the {@link PushMeasure} send a notification
	 * with a given {@link Value}, this {@link Value} is stored into a variable
	 * so that it can be retrieved at any time through the method
	 * {@link PullMeasure#get()}.
	 * 
	 * @param push
	 *            a {@link PushMeasure} to backup
	 * @param initialValue
	 *            the {@link Value} to return before the next notification of
	 *            the {@link PushMeasure} is sent
	 * @return a {@link PullMeasure} allowing to retrieve the last value sent by
	 *         the {@link PushMeasure}, or the initial value if it did not send
	 *         any
	 */
	public <Value> PullMeasure<Value> createPullFromPush(
			final PushMeasure<Value> push, Value initialValue) {
		final Object[] cache = { initialValue };
		final MeasureListener<Value> listener = new MeasureListener<Value>() {

			@Override
			public void measureGenerated(Value value) {
				cache[0] = value;
			}
		};
		push.register(listener);
		return new PullMeasure<Value>() {

			@Override
			public String getName() {
				return push.getName();
			}

			@Override
			public String getDescription() {
				return push.getDescription();
			}

			@SuppressWarnings("unchecked")
			@Override
			public Value get() {
				return (Value) cache[0];
			}

			@Override
			protected void finalize() throws Throwable {
				push.unregister(listener);
				super.finalize();
			}
		};
	}

	/**
	 * Create a {@link PushMeasure} which checks at regular intervals the value
	 * of a {@link PullMeasure}. If the value have changed since the last check
	 * (or since the creation of the {@link PushMeasure}), a notification will
	 * be generated by the {@link PushMeasure} with the new {@link Value}.<br/>
	 * <br/>
	 * Notice that if the period is two small, the checking process could have a
	 * significant impact on performances, because a {@link Thread} is run in
	 * parallel to check regularly the {@link Value} modifications. If the
	 * period is too big, you could miss relevant notifications, especially if
	 * the {@link PullMeasure} change to a new {@link Value} and change back to
	 * its previous {@link Value} between two consecutive checks. In such a
	 * case, no notification will be sent because the {@link Value} during the
	 * two checks is equal.
	 * 
	 * @param pull
	 *            the {@link PullMeasure} to cover
	 * @param period
	 *            the number of milliseconds between each check
	 * @return a {@link PushMeasure} which will notify any change occurred on
	 *         the {@link PullMeasure} at the given frequency
	 */
	public <Value> PushMeasure<Value> createPushFromPull(
			PullMeasure<Value> pull, final long period) {
		SimplePushMeasure<Value> push = new SimplePushMeasure<>(pull.getName(),
				pull.getDescription());
		final WeakReference<PullMeasure<Value>> weakPull = new WeakReference<PullMeasure<Value>>(
				pull);
		final WeakReference<SimplePushMeasure<Value>> weakPush = new WeakReference<SimplePushMeasure<Value>>(
				push);
		final Value initialValue = pull.get();
		/*
		 * TODO Use a static thread to run the checks of all the measures
		 * created that way. Using a WeakHashMap could probably do the trick.
		 */
		Thread thread = new Thread(new Runnable() {
			private Value lastValue = initialValue;

			@Override
			public void run() {
				boolean isThreadNeeded = true;
				long alreadyConsumed = 0;
				while (isThreadNeeded) {
					if (alreadyConsumed > period) {
						long realConsumption = alreadyConsumed;
						long missed = alreadyConsumed / period;
						alreadyConsumed = alreadyConsumed % period;
						log.warning("Too much time consumed in the last measuring ("
								+ realConsumption
								+ ">"
								+ period
								+ "), ignore the "
								+ missed
								+ " pushes missed and consider it has consumed "
								+ alreadyConsumed);
					} else {
						// usual case.
					}
					try {
						Thread.sleep(period - alreadyConsumed);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					long measureStart = System.currentTimeMillis();

					PullMeasure<Value> pull = weakPull.get();
					SimplePushMeasure<Value> push = weakPush.get();
					if (pull == null || push == null) {
						isThreadNeeded = false;
					} else {
						Value value = pull.get();
						if (value == lastValue || value != null
								&& value.equals(lastValue)) {
							// still the same, don't notify
						} else {
							lastValue = value;
							push.push(value);
						}
					}
					pull = null;
					push = null;

					long measureEnd = System.currentTimeMillis();
					alreadyConsumed = measureEnd - measureStart;
				}
			}
		});
		thread.setDaemon(true);
		thread.start();
		return push;
	}

	/**
	 * Create {@link PullMeasure}s based on the getters available from an
	 * instance, whatever it is. The {@link Class} of the instance is analyzed
	 * to retrieve its public methods and a {@link PullMeasure} is built for
	 * each method which use a getter-like signature. The name of the method is
	 * further exploited to identify the measure, such that the map returned use
	 * the name of the method (without "get") as a key which maps to the
	 * {@link PullMeasure} built from this method. The {@link PullMeasure}
	 * itself is named by using the name of the method.
	 * 
	 * @param object
	 *            the {@link Object} to cover
	 * @return the {@link Map} which contains the names of the getter methods
	 *         and the corresponding {@link PullMeasure} built from them
	 */
	public Map<String, PullMeasure<?>> createPullsFromGetters(
			final Object object) {
		Map<String, PullMeasure<?>> measures = new HashMap<String, PullMeasure<?>>();
		Class<? extends Object> clazz = object.getClass();
		for (final Method method : clazz.getMethods()) {
			if (method.getParameterTypes().length == 0
					&& method.getReturnType() != null
					&& !method.getName().equals("getClass")
					&& method.getName().matches("get[^a-z].*")) {
				String key = method.getName().substring(3);
				// TODO exploit return type to restrict the generics
				measures.put(key, new SimplePullMeasure<Object>(key) {

					@Override
					public Object get() {
						try {
							return method.invoke(object);
						} catch (IllegalAccessException
								| IllegalArgumentException
								| InvocationTargetException e) {
							throw new RuntimeException(e);
						}
					}
				});
			} else {
				// not a getter, ignore it
			}
		}
		return measures;
	}

	/**
	 * Create {@link PullMeasure}s based on the fields available from an
	 * instance, whatever it is. The {@link Class} of the instance is analyzed
	 * to retrieve its public fields and a {@link PullMeasure} is built for each
	 * of them. The name of the field is further exploited to identify the
	 * measure, such that the map returned use the name of the field as a key
	 * which maps to the {@link PullMeasure} built from this field. The
	 * {@link PullMeasure} itself is named by using the name of the field.
	 * 
	 * @param object
	 *            the {@link Object} to cover
	 * @return the {@link Map} which contains the names of the getter methods
	 *         and the corresponding {@link PullMeasure} built from them
	 */
	public Map<String, PullMeasure<?>> createPullsFromFields(final Object object) {
		Map<String, PullMeasure<?>> measures = new HashMap<String, PullMeasure<?>>();
		Class<? extends Object> clazz = object.getClass();
		for (final Field field : clazz.getFields()) {
			String key = field.getName();
			// TODO exploit return type to restrict the generics
			measures.put(key, new SimplePullMeasure<Object>(key) {

				@Override
				public Object get() {
					try {
						return field.get(object);
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new RuntimeException();
					}
				}
			});
		}
		return measures;
	}

}
