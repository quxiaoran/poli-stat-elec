package auspice;

import dataholder.District;
import dataholder.Grade;
import dataholder.Poll;

import java.util.Map;

/**
 * Predict democratic votes percent using district-level polls or Blairvoyance. The weight of the polls versus the
 * shifted fundamentals is a logistic function.
 */
public class LogisticPollCalculator extends PollCalculator {

    /**
     * A map of each 538 pollster grade to the "quality point" score associated with that grade.
     */
    private final Map<Grade, Double> gradeQualityPoints;

    /**
     * The (positive) number to multiply by the number of days a poll was taken before the election, then plug into e^-x
     * to get the age adjustment for poll weighting. Should be less than 1.
     */
    private final double daysCoefficient;

    /**
     * The maximum weight of the polls compared to the shifted fundamentals, from 0 to 1.
     */
    private final double maxPollWeight;

    /**
     * How much to shift the logistic function in x.
     */
    private final double logisticShift;

    /**
     * What the steepness of the logistic function should be.
     */
    private final double logisticSteepness;

    /**
     * The weight to give to Blairvoyance relative to the shifted fundamentals in districts with no polls, from 0 to 1.
     */
    private final double blairvoyanceWeight;

    /**
     * The standard deviation of the Blairvoyance predicted democrat percent.
     */
    private final double blairvoyanceStDv;

    /**
     * Default constructor.
     *
     * @param pollAverager       The method to use for averaging polls when a district has multiple polls.
     * @param gradeQualityPoints A map of each 538 pollster grade to the "quality point" score associated with that
     *                           grade.
     * @param daysCoefficient    The (positive) number to multiply by the number of days a poll was taken before the
     *                           election, then plug into e^-x to get the age adjustment for poll weighting. Should be
     *                           less than 1.
     * @param maxPollWeight      The maximum weight of the polls compared to the shifted fundamentals, from 0 to 1.
     * @param logisticShift      How much to shift the logistic function by in x.
     * @param logisticSteepness  What the steepness of the logistic function should be.
     * @param blairvoyanceWeight The weight to give to Blairvoyance relative to the shifted fundamentals in districts
     *                           with no polls, from 0 to 1.
     * @param blairvoyanceStDv   The standard deviation of the Blairvoyance predicted democrat percent.
     */
    public LogisticPollCalculator(PollAverager pollAverager, Map<Grade, Double> gradeQualityPoints,
                                  double daysCoefficient, double maxPollWeight, double logisticShift,
                                  double logisticSteepness, double blairvoyanceWeight, double blairvoyanceStDv) {
        super(pollAverager);
        this.gradeQualityPoints = gradeQualityPoints;
        this.daysCoefficient = daysCoefficient;
        this.maxPollWeight = maxPollWeight;
        this.logisticShift = logisticShift;
        this.logisticSteepness = logisticSteepness;
        this.blairvoyanceWeight = blairvoyanceWeight;
        this.blairvoyanceStDv = blairvoyanceStDv;
    }

    /**
     * Calculate democratic vote percent and standard deviation using the polls in that district or Blairvoyance. This
     * modifies the district's finalDemPercent and finalDemStDv.
     *
     * @param district A district with the generic-corrected average dem percent and standard deviation already
     *                 calculated.
     */
    @Override
    public void calculatePolls(District district) {
        double pollAverage;
        double pollStDv;
        double pollWeight;
        if (district.hasPolls()) {
            pollAverage = pollAverager.getAverage(district.getPolls());
            pollStDv = pollAverager.getStDv(district.getPolls());
            double x = 0;
            //Sum of qualityPoints*e^(-daysCoefficient*t)
            for (Poll poll : district.getPolls()) {
                x += (Math.exp(-daysCoefficient * poll.getDaysBeforeElection())) * gradeQualityPoints.get(poll.getGrade());
            }
            //Weight logistically
            pollWeight = maxPollWeight / (1 + Math.exp(-logisticSteepness * (x - logisticShift)));
        } else {
            //If there's no polls, just use Blairvoyance.
            pollAverage = district.getBlairvoyanceDemPercent();
            pollStDv = blairvoyanceStDv;
            pollWeight = blairvoyanceWeight;
        }

        //Weights are normalized already so we don't need to divide or anything
        district.setAuspiceDemPercent(pollWeight * pollAverage + (1 - pollWeight) * district.getBigmoodDemPercent());

        //Pythagorean theorem of statistics
        district.setAuspiceStDv(Math.sqrt(Math.pow(pollStDv * pollWeight, 2) + Math.pow(district.getBigmoodStDv() * (1 - pollWeight), 2)));
    }
}
