package bogdanpc.linearsync.cli.boundary;

import bogdanpc.linearsync.linear.entity.LinearStateType;
import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.validator.OptionValidatorException;

public class StateTypeConverter implements Converter<LinearStateType, ConverterInvocation> {

    @Override
    public LinearStateType convert(ConverterInvocation invocation) throws OptionValidatorException {
        try {
            return LinearStateType.fromValue(invocation.getInput());
        } catch (IllegalArgumentException e) {
            throw new OptionValidatorException(e.getMessage());
        }
    }
}
