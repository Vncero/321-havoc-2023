/* (C) Robolancers 2024 */
package org.robolancers321.intake.commands;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.subsystems.Intake;

public class RunOuttake extends CommandBase {
  public double velocity;

  Intake intake;

  public RunOuttake(Intake intake, double velocity) {
    this.intake = intake;
    this.velocity = velocity;
    addRequirements(intake);
  }

  @Override
  public void initialize() {
    intake.setIntakeVelocity(-velocity);
  }

  @Override
  public void end(boolean interrupted) {
    intake.setIntakeVelocity(0);
  }
}
